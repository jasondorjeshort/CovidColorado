**The exclusion query `Voc.build()` prints is a list of object addresses, and
the variant count printed beside it is wrong.** `variants/Voc.java` `build()`,
the "Output (display)" block, in the tree as of the LAPIS work.

Two defects in the same dozen lines, both confirmed by reading the code:

`sb.append(variant)` appends a `Variant`, and `variants/Variant.java` declares
no `toString`. So the line that is meant to be a paste-able cov-spectrum
exclusion query -- `!(nextcladePangoLineage:XFG*)&!(...)` -- comes out as
`!(variants.Variant@1a2b3c)&!(...)` and is useless for the manual fallback it
exists to serve. `variant.name` is what the neighbouring `sb2` loop uses and is
what this one wants.

`System.out.println(String.format("%,d total variants", variants.size() - 1))`
runs before the "others" variant is added, and subtracts one on top of that.
The printed number is therefore two less than the count the run goes on to use,
and one less than the count at the moment it is printed. There is no
compensating "others" in the set at that point for the `- 1` to be excluding.

Not fixed here: this pass was doc-only, and the second one wants a decision
about which count the line is meant to report before the arithmetic is changed.
Both are log-only; nothing computed depends on either.
