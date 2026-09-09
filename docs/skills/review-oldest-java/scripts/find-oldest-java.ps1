[CmdletBinding()]
param(
    [ValidateRange(1, 1000)]
    [int] $Count = 10
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = git rev-parse --show-toplevel
if ($LASTEXITCODE -ne 0) {
    throw 'Run this script inside a Git repository.'
}

Push-Location $repositoryRoot
try {
    $javaPaths = @(git ls-files -- '*.java')
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not enumerate tracked Java files.'
    }

    $currentPaths = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::Ordinal
    )
    foreach ($path in $javaPaths) {
        [void] $currentPaths.Add($path)
    }

    $touches = @{}
    $currentCommit = $null
    $currentEpoch = [int64] 0
    $currentDate = $null
    $currentSubject = $null

    $history = git log '--format=@@@%H%x09%ct%x09%cs%x09%s' --name-only `
        --diff-filter=ACMRT -- '*.java'
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not read Java file history.'
    }

    foreach ($line in $history) {
        if ($line.StartsWith('@@@', [System.StringComparison]::Ordinal)) {
            $parts = $line.Substring(3).Split("`t", 4)
            if ($parts.Count -ne 4) {
                throw "Could not parse Git history header: $line"
            }
            $currentCommit = $parts[0]
            $currentEpoch = [int64] $parts[1]
            $currentDate = $parts[2]
            $currentSubject = $parts[3]
            continue
        }

        if (-not $line -or -not $currentPaths.Contains($line)) {
            continue
        }

        if (-not $touches.ContainsKey($line) -or
            $currentEpoch -gt $touches[$line].Epoch) {
            $touches[$line] = [pscustomobject] @{
                Path = $line
                Epoch = $currentEpoch
                Date = $currentDate
                Commit = $currentCommit
                Subject = $currentSubject
            }
        }
    }

    <#
      Read the review ledger. A reviewed path leaves the queue permanently:
      this is a one-time pass over every file, not a rotation, so a line is a
      record that the pass happened and never expires. Nothing here compares
      dates, which is why a later commit to a reviewed file does not put it
      back, and why the committer's timezone cannot matter.

      The date on each line is kept as history, not as a key. A future review
      of a different kind wants its own ledger file rather than a reinterpreted
      one.
    #>
    $ledgerPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'reviewed.txt'
    $reviewed = [System.Collections.Generic.Dictionary[string, int]]::new(
        [System.StringComparer]::Ordinal
    )
    $staleLedger = @()
    if (Test-Path -LiteralPath $ledgerPath) {
        $lineNumber = 0
        foreach ($entry in @(Get-Content -LiteralPath $ledgerPath)) {
            $lineNumber++
            $trimmed = $entry.Trim()
            if (-not $trimmed -or
                $trimmed.StartsWith('#', [System.StringComparison]::Ordinal)) {
                continue
            }

            $fields = $trimmed.Split(' ', 2)
            if ($fields.Count -ne 2) {
                throw "Could not parse reviewed.txt line ${lineNumber}: $entry"
            }

            <#
              The parsed value is discarded: nothing orders on it any more.
              The parse stays as a format check, so a malformed date is still
              an error rather than a line that silently means nothing.
            #>
            $reviewDate = [datetime]::MinValue
            $parsed = [datetime]::TryParseExact(
                $fields[0], 'yyyy-MM-dd', [cultureinfo]::InvariantCulture,
                [System.Globalization.DateTimeStyles]::None, [ref] $reviewDate)
            if (-not $parsed) {
                throw "Could not parse reviewed.txt date on line ${lineNumber}: $entry"
            }

            $reviewedPath = $fields[1].Trim()
            if (-not $currentPaths.Contains($reviewedPath)) {
                $staleLedger += "$reviewedPath (line ${lineNumber}, not a tracked Java path)"
                continue
            }

            if ($reviewed.ContainsKey($reviewedPath)) {
                $staleLedger += "$reviewedPath (line ${lineNumber}, already recorded on line $($reviewed[$reviewedPath]))"
                continue
            }

            $reviewed[$reviewedPath] = $lineNumber
        }
    }

    <#
      Reviewed paths are gone from the queue for good, so what remains is
      ordered on the one fact left: the oldest Git touch first, path breaking
      ties. The sort's output is what gets wrapped, not its input: Sort-Object
      decides the shape of what it emits, and a single match left unwrapped is
      a bare PSCustomObject whose .Count is $null rather than 1.
    #>
    $unsorted = @(
        foreach ($path in $javaPaths) {
            if ($touches.ContainsKey($path) -and -not $reviewed.ContainsKey($path)) {
                $touches[$path]
            }
        }
    )
    $remaining = @($unsorted | Sort-Object Epoch, Path)

    $candidates = @($remaining | Select-Object -First $Count)

    $rank = 0
    foreach ($candidate in $candidates) {
        $rank++
        $status = @(git status --short -- $candidate.Path)
        if ($LASTEXITCODE -ne 0) {
            throw "Could not read status for $($candidate.Path)."
        }

        [pscustomobject] @{
            Rank = $rank
            LastTouch = $candidate.Date
            Commit = $candidate.Commit
            Status = if ($status.Count -eq 0) { 'clean' } else { $status -join ' ' }
            Path = $candidate.Path
            Subject = $candidate.Subject
        }
    }

    if ($remaining.Count -eq 0) {
        Write-Output 'Every tracked Java file with a commit has been reviewed. Nothing left in the queue.'
    } else {
        Write-Output "Remaining unreviewed: $($remaining.Count) of $($javaPaths.Count) tracked Java files."
    }

    $uncommitted = @($javaPaths | Where-Object { -not $touches.ContainsKey($_) })
    if ($uncommitted.Count -gt 0) {
        Write-Warning ('Excluded tracked Java paths with no commit: ' +
            ($uncommitted -join ', '))
    }

    if ($staleLedger.Count -gt 0) {
        Write-Warning ('Dead reviewed.txt lines, safe to sweep: ' +
            ($staleLedger -join ', '))
    }

    <#
      The ledger above was read from the working tree, so a line written by a
      pass that never committed has already retired its file from the rows
      printed. Nothing else would notice: the file is gone from the queue and
      no commit says it was reviewed. Say so, rather than let the next pass
      start on top of an unfinished one.
    #>
    $ledgerStatus = @(git status --short -- $ledgerPath)
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not read status for reviewed.txt.'
    }
    if ($ledgerStatus.Count -gt 0) {
        Write-Warning ('reviewed.txt has uncommitted changes: a previous pass wrote ' +
            'its line and did not commit. Finish that commit before starting another pass.')
    }
} finally {
    Pop-Location
}
