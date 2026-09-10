**The two hardcoded user paths are the only thing stopping this program running
on another machine.** `charts/Charts.java` `TOP_FOLDER` and `nwss/Nwss.java`
`GIT_LOCATION`, both absolute paths under one user's downloads folder.

Everything else the program needs it fetches or derives: the CDC CSV and the
LAPIS JSON go under the system temp directory, and the region list is a
resource on the classpath. These two are the exceptions, and a second checkout
cannot draw a single chart without editing Java. A properties file read at
startup, or an environment variable with the current value as the default,
would fix it in a few lines -- the values are read once each and never written.

Not a defect. This is a single-author batch job that has only ever run in one
place, and hardcoding was the right amount of work for that. It becomes worth
doing the first time somebody wants to run it somewhere else, or wants two
output trees to compare. Whoever does it should update the paths section of
CLAUDE.md in the same change, since that is where the expectation is currently
written down.
