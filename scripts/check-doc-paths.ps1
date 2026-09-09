<#
.SYNOPSIS
  Reports code paths named in docs/ and .claude/ that no longer exist.

.DESCRIPTION
  The docs under docs/ deliberately name a small number of exact code anchors
  (see docs/README.txt, CONVENTIONS / ANCHORS). Those anchors are the part of a
  doc that rots silently when code is renamed or moved. This script extracts
  every path-shaped token from the docs and reports the ones that do not resolve
  on disk, so staleness is a check you can run instead of something you have to
  remember.

  Tokens are resolved against the repo root, the Java source root, and docs/.

  .claude/commands/ is scanned on the same pass. A pointer file there holds one
  path into docs/skills/ and no procedure of its own, so a renamed card breaks
  its slash command with nothing to show for it -- the same rot as a stale doc
  anchor, and checkable the same way. That is also why md is in the extension
  list below: a card is docs/skills/<name>/SKILL.md, and without md a pointer's
  path resolves only as far as that directory, so a card directory left without
  its SKILL.md would still pass, and the cards' references to one another would
  go unchecked too.

  Prose containing slashes ("sum/min/max", "load/render") looks exactly like a
  directory path, so directory-form tokens are only checked when their first
  segment is a real top-level source directory. File-form tokens (ending in a
  known source extension) are always checked. This biases toward
  under-reporting: a missed anchor is better than a wall of false positives
  nobody reads.

  A doc paragraph containing "[no-check: reason]" is skipped entirely -- the
  escape hatch for deliberately naming a path that does not exist (proposed
  files, deleted files, the runtime paths this program reads and writes outside
  the checkout). See docs/README.txt, CONVENTIONS / ANCHORS.

  The review ledger at docs/skills/review-oldest-java/reviewed.txt is skipped
  whole, for the mirror-image reason: it logs what was reviewed and when, so its
  paths are historical facts rather than anchors, and deleting a reviewed file
  should not fail this check.

  docs/active/ tokens need no marker: those files are created when work starts
  and deleted when it lands, so a durable doc naming one is naming something
  expected to come and go. The skip is blanket, so the permanent README under
  findings/ rides along in it and resolves on its own anyway.

  Exits 1 if any anchor is unresolved, so it can gate a commit if wanted.

.PARAMETER Docs
  Directories to scan, replacing the defaults. Tokens still resolve against the
  repo, not against the scanned directory.

.EXAMPLE
  .\scripts\check-doc-paths.ps1
#>
[CmdletBinding()]
param(
    [string[]] $Docs
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$docsRoot = Join-Path $repoRoot 'docs'

if ($Docs) {
    foreach ($dir in $Docs) {
        if (-not (Test-Path $dir)) {
            Write-Error "Scan directory not found: $dir"
            exit 1
        }
    }
    $scanRoots = @($Docs)
} else {
    # The docs, and the slash-command pointers that name cards inside them. A
    # missing .claude/ is not an error: it is the agent harness's directory,
    # not the repo's, and a checkout without one still has docs worth checking.
    $scanRoots = @($docsRoot, (Join-Path $repoRoot '.claude')) |
        Where-Object { Test-Path $_ }
    if (-not $scanRoots) {
        Write-Error "Docs directory not found: $docsRoot"
        exit 1
    }
}

# Longer extensions must precede their own prefixes (json before js), so a
# ".json" path is not matched as ".js" plus trailing garbage.
$extensions = 'java|json|js|xml|txt|csv|ps1|gradle|md'

# Two or more slash-separated segments, starting with a lowercase segment,
# ending either in a known source extension or a trailing slash.
$pattern = "(?<![\w./-])(?:src/main/java/)?[a-z][a-zA-Z0-9_.-]*(?:/[a-zA-Z0-9_.-]+)+(?:\.(?:$extensions)(?![\w])|/)"

# Where a token may resolve -- fixed to the repo, independent of what is being
# scanned. docs/ lets README.txt's own map entries ("reference/foo.txt") be
# checked; src/main/java is the shorthand the docs use for a package path.
$searchRoots = @(
    $repoRoot
    Join-Path $repoRoot 'src/main/java'
    $docsRoot
) | Where-Object { Test-Path $_ }

# Real top-level directories, discovered rather than hardcoded so the allowlist
# cannot drift from the tree.
$roots = @{}
foreach ($base in @($repoRoot, (Join-Path $repoRoot 'src/main/java'), $docsRoot)) {
    if (-not (Test-Path $base)) { continue }
    foreach ($d in Get-ChildItem -LiteralPath $base -Directory) { $roots[$d.Name] = $true }
}

$missing = @()
$checked = 0
$skipped = 0

foreach ($file in Get-ChildItem -Path $scanRoots -Recurse -Include *.txt, *.md -File) {
    $relDoc = $file.FullName.Substring($repoRoot.Length + 1)

    # The review ledger is an append-only log of which files were reviewed and
    # when, not prose making claims about the current tree. Its paths stay true
    # after a file is deleted, so checking them would turn an ordinary deletion
    # into a standing failure here. find-oldest-java.ps1 already reports its own
    # dead lines, which is where that belongs.
    if ($relDoc.Replace('\', '/') -eq 'docs/skills/review-oldest-java/reviewed.txt') {
        continue
    }

    $allLines = @(Get-Content -LiteralPath $file.FullName)

    # Escape hatch for text that legitimately names a path which does not exist:
    # a proposed file, a deleted one, a runtime artifact outside the checkout.
    # The marker takes an optional reason -- "[no-check: proposed]" -- and
    # suppresses the whole blank-line-delimited paragraph it appears in, because
    # a path and the sentence explaining it routinely land on different wrapped
    # lines.
    $suppressed = New-Object 'System.Collections.Generic.HashSet[int]'
    $start = 0
    for ($i = 0; $i -le $allLines.Count; $i++) {
        $atEnd = ($i -eq $allLines.Count) -or ($allLines[$i].Trim() -eq '')
        if (-not $atEnd) { continue }
        $para = if ($i -gt $start) { $allLines[$start..($i - 1)] } else { @() }
        if ($para -match '\[no-check\b') {
            for ($j = $start; $j -lt $i; $j++) { [void]$suppressed.Add($j) }
        }
        $start = $i + 1
    }

    $lineNo = 0
    foreach ($line in $allLines) {
        $lineNo++
        if ($suppressed.Contains($lineNo - 1)) { continue }
        foreach ($m in [regex]::Matches($line, $pattern)) {
            $token = $m.Value
            $segments = $token.TrimEnd('/') -split '/'
            $isDir = $token.EndsWith('/')

            # Documented placeholders: charts/ChartXxx.java, or the
            # metasyntactic foo a doc reaches for when it spells a path format
            # out. Any segment, not just the last: a placeholder card is
            # docs/skills/foo/SKILL.md, where the invented name is the
            # directory.
            if ($token -match 'Xxx' -or ($segments -match '^(foo|bar)(\.|$)')) { $skipped++; continue }
            # A docs/active/ file is deleted when the work it tracks lands, so
            # a durable doc naming one is naming something expected to come and
            # go, not a rotting anchor. Blanket: the permanent one resolves anyway.
            if ($token -match '^(docs/)?active/.') { $skipped++; continue }
            # URLs and external hosts.
            if ($token -match '^(https?|www\.)' -or $segments[0] -match '\.(com|org|net|io|gov)$') { $skipped++; continue }
            # Prose joining two filenames, e.g. "Nwss.java/Lapis.java".
            $interior = $segments[0..([Math]::Max(0, $segments.Count - 2))]
            if ($interior -match "\.($extensions)`$") { $skipped++; continue }
            # Directory-form prose ("sum/min/max/") unless rooted in a real dir.
            if ($isDir -and -not $roots.ContainsKey($segments[0]) -and $segments[0] -ne 'src') { $skipped++; continue }

            $checked++
            $resolved = $false
            foreach ($root in $searchRoots) {
                if (Test-Path -LiteralPath (Join-Path $root $token)) { $resolved = $true; break }
            }
            if (-not $resolved) {
                $missing += [pscustomobject]@{ Doc = $relDoc; Line = $lineNo; Path = $token }
            }
        }
    }
}

$unique = @($missing | Sort-Object Path, Doc, Line)

if ($unique.Count -eq 0) {
    Write-Host "check-doc-paths: $checked anchors checked ($skipped prose-like tokens skipped), all resolve." -ForegroundColor Green
    exit 0
}

Write-Host "check-doc-paths: $checked anchors checked ($skipped skipped), $($unique.Count) unresolved." -ForegroundColor Yellow
$unique | Format-Table -AutoSize Doc, Line, Path | Out-String | Write-Host
Write-Host "Unresolved anchors are usually renamed or moved code. Fix the doc." -ForegroundColor Yellow
exit 1
