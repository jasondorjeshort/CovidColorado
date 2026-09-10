**Raising the LAPIS sequence floor would cut the lineage legend to something a
reader can actually use.** `variants/Lapis.java`, `MIN_SEQUENCES`, currently
20.

At 20, the pull leaves roughly 105 lineages on the national chart and about 197
on the Colorado one -- more colours than a legend can distinguish and more
lines than a chart can show. Raising it to 50 leaves about 89 candidates before
`variants/VocSewage.java` merges anything, which is close enough to a legible
chart to be worth trying. The change is one constant; the work is looking at
the resulting charts and deciding, which is why it is an idea and not a patch.

Not a defect. Twenty is a real threshold with a real reason -- it is what stops
a two-sequence lineage from surviving `VocSewage`'s ten-smoothed-days rule --
and nothing computed downstream is wrong at that value. What is left is a
judgement about how much detail a chart should carry, and that judgement will
want revisiting whenever US sequencing volume moves again, in either direction.
