import {Territory} from "./Territory";
import {TileSet} from "../TileSet";

export interface TerritoryList {
    territories: Record<string, Territory>
    tiles: TileSet
    timestamp: number
}