import {TerritoryList} from "../api/territories/TerritoryList";
import * as L from 'leaflet'
import {LatLng} from "leaflet";
import {Position} from "../api/IPosition";
import {LayerType} from "./api/LayerType";
import {TerritoryLayer} from "./layers/territories/TerritoryLayer";
import {LinkLayer} from "./layers/territories/LinkLayer";
import {TileSetLayer} from "./layers/TileSetLayer";

let CENTER = new Position(270, -1570)

export class LeafletManager {
    private readonly raw: L.Map

    private _tiles?: TileSetLayer

    get tiles(): TileSetLayer {
        return this._tiles!
    }

    set tiles(tile: TileSetLayer) {
        let first = this._tiles == undefined

        if (this._tiles != undefined) {
            this._tiles.removeFrom(this.raw)
        }

        this._tiles = tile
        tile.addTo(this.raw)

        if (first)
            this.raw.setView(this.coordsOf(CENTER), 2)
    }

    private constructor() {
        this.raw = L.map(document.querySelector("#leaflet")! as HTMLElement, {
            preferCanvas: true,
            crs: L.CRS.Simple,

            inertia: true,
            inertiaMaxSpeed: 1000,
            zoomControl: false
        })

        L.control.zoom({
            position: "bottomleft"
        }).addTo(this.raw)

        TerritoryLayer.addTo(this.raw)
        LinkLayer.addTo(this.raw)

        this.raw.on("zoom", _ => {
            document.querySelector("#leaflet")?.setAttribute("zoom", this.raw.getZoom().toString())
        })
    }

    update(list: TerritoryList) {
        if (this._tiles?.id != list.tiles.id)
            Leaflet.tiles = new TileSetLayer(list.tiles)

        TerritoryLayer.update(list)
    }

    shuffle() {
        this.raw.fire("viewreset")
    }

    positionOf(latLng: LatLng): Position {
        return new Position(
            this.tiles.start.x - (latLng.lng * -8),
            this.tiles.start.z - (latLng.lat * 8)
        )
    }

    coordsOf(position: Position): LatLng {
        return new LatLng(
            (position.z - this.tiles.start.z) / -8,
            (position.x - this.tiles.start.x) / 8
        )
    }

    add(layer: L.Layer | LayerType) {
        layer.addTo(this.raw)
    }

    remove(layer: L.Layer | LayerType) {
        layer.removeFrom(this.raw)
    }

    static INSTANCE = new LeafletManager()
}

let Leaflet = LeafletManager.INSTANCE

export {Leaflet}