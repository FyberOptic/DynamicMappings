# DynamicMappings

This project programmatically determines class/field/method mappings for Minecraft snapshots.  It began with Minecraft 1.9 and continues into 1.11 (and probably whatever is newest as of reading).  It's in a constant state of evolution to keep up with changes from Mojang.

Please note that this is not a complete set of mappings!  It's not comparable to MCP or other static mappings projects.  It doesn't use generic class comparison techniques, it uses targeted detection to ensure accuracy.  Mapping detection routines are generally added on an as-needed basis, or when they're convenient to add into existing ones.  They can also be difficult and time-consuming to write, so many mappings you're familiar with seeing may not presently exist.

While not often, you will occasionally encounter names which are not always the same as MCP, because being on the bleeding edge of snapshots means we have to come up with our own names.  They may be changed later for better code compatibility.





#### Contributing

You're welcome to contribute to this project, but please consider its nature beforehand.  DynamicMappings is a house of cards.  It's very hierarchical, with one mapping depending on other mappings before it.  As Minecraft evolves, it generally breaks mapping detection routines, and thus many mappings further down the chain fall as well.  This is good in a way, because it lets us see what's changed and how to adapt where necessary.  Sometimes fixing a single mapping fixes an entire chain.  

The absolute worst case scenario is a mapping misdetection, where something is detected as something it isn't at some point in the future, which can lead to many confusing issues to track down and solve.  We try to avoid this in particular when at all possible by trying to be reasonably certain of a mapping based on relative information.  You might do this through values in the constant pool, class relations, code patterns, fields, methods, etc, etc, etc.  Sometimes getting a mapping directly from its owner class is impossible to do with any degree of certainty, so you have to find locations elsewhere in the game where you can more accurately guarantee a match.

Changing existing mappings routines is generally not necessary.  Mappings are already time-consuming enough to write, much less to verify contributions, so try to stick to adding new mappings unless there's an instance where something can be noticeably improved.  I'm open to discussing it in such a case.

Try to keep pull requests small, otherwise it takes much longer to process.  Your PR may be rejected if it doesn't appear to adequately verify what it's adding, depending on what that is.  It's not personal, so don't take it that way.  Mojang could change something at any time and break everything, so ensuring the integrity of mappings is always the priority.

