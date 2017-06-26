# YoRHa Watch Face
Watch face w/ YoRHa stylings from NieR:Automata

# Design TODO
- Menu animations (almost there)
- Box around text? Animate boxes in w/ horizontal movement
- Buzz on hour
- Dark theme (main menu style?)
- Complications w/ chips from UI
- Options in app
- Content to make this page look pretty (text anim gif, overall look)

# Tech TODO
- Text animation render rate independent of main -- is this even possible? Dynamic rates instead? (Battery impact is signficant @ ~30fps)
- Switching to ambient mid animation can result in no text being displayed (probably other weird combos)
- Text animation cooldown  (to prevent: leave ambient -> animation -> time % 10 == 0 -> animation restart)
- What the hecks an app (lots to config)
