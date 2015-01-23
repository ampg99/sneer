module Main where
import Color (..)
import Graphics.Collage (..)
import Graphics.Element (..)
import Signal (..)
import Time (..)
import Window

main = clock <~ every second ~ Window.dimensions

clock t (sw, sh) =
  collage 400 400
    [ filled    lightGrey   (ngon 12 110)
    , outlined (solid grey) (ngon 12 110)
    , hand orange   100  t
    , hand charcoal 100 (t/60)
    , hand charcoal 60  (t/720)
    ]
  |> container sw sh middle

hand clr len time =
  let angle = degrees (90 - 6 * inSeconds time)
  in
      segment (0,0) (fromPolar (len,angle))
        |> traced (solid clr)

