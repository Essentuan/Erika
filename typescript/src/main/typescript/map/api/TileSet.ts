import {IPosition} from "./IPosition";
import {Region} from "./Region";

export interface TileSet extends Region {
    id: string

    nativeZoom: number
    zoom: ZoomRange
}

export interface ZoomRange {
    min: number
    max: number
}