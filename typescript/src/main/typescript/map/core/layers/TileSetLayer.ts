import {TileSet, ZoomRange} from "../../api/TileSet";
import {IPosition} from "../../api/IPosition";
import {LayerType} from "../api/LayerType";
import * as L from 'leaflet'

export class TileSetLayer implements TileSet, LayerType {
    id: string;

    start: IPosition;
    end: IPosition;

    nativeZoom: number;
    zoom: ZoomRange;

    private readonly layer: L.TileLayer

    constructor(tileSet: TileSet) {
        this.id = tileSet.id

        this.start = tileSet.start
        this.end = tileSet.end

        this.nativeZoom = tileSet.nativeZoom
        this.zoom = tileSet.zoom

        this.layer = L.tileLayer(`/tile/${this.id}/{x}/{y}/{z}`, {
            attribution: "The Simple Ones",
            tileSize: 500,
            zoomOffset: this.zoom.min,
            maxZoom: this.zoom.max - this.zoom.min,
            minZoom: 0
        })
    }

    addTo(object: L.Map | L.LayerGroup): void {
        this.layer.addTo(object)
    }

    removeFrom(object: L.Map | L.LayerGroup): void {
        if (object instanceof L.Map)
            this.layer.removeFrom(object)
        else
            object.removeLayer(this.layer)
    }
}