# Kickset design notes

Reading this as: a precision trade calculator for pipe fitters on site and in the shop, with a steel layout table language (layout dye, soapstone marks, dimension lines), leaning toward Archivo Narrow plus Archivo on a layout blue and mill steel palette.

Dials:
- DESIGN_VARIANCE 3: eyes land in the same place on every calculator (inputs, sketch panel, working).
- MOTION_INTENSITY 2: motion only confirms actions (answer crossfade 150 ms, dimension line redraw 200 ms), snaps under reduced motion.
- VISUAL_DENSITY 6: one screenful carries the inputs, the sketch and the answer on a 6 inch phone. The working lines and Save to job sit one short scroll below; holding them above the fold as well would mean cutting either the sketch or the working, and the shown working is the whole point of the app.

The one memorable thing: shown working. Every answer sits on a dimension line in a sketch panel, with the arithmetic printed under it in both units.

Tokens (from blueprint section 7): Plate #EEF1F4/#10161D, Sheet #F7F9FB/#18212A, Ink #16202B/#E3E8ED, Scale #4F5B67/#9AA6B2, Layout Blue #2152B0/#86A8EA, On Blue #F7F9FB/#0E1A2E, Rule #C9D1D9/#2A3540, Weld Red #A8322A/#F08A80.
Radius: 4 / 8 / 16 dp. The sketch panel is the only card on a calculator screen; lists use dividers.
