import * as L from 'leaflet'
import {LayerType} from "../../api/LayerType";
import {Polyline} from "leaflet";
import {Leaflet} from "../../Leaflet";
import {TerritoryLayer} from "./TerritoryLayer";

export class Link implements LayerType {
    readonly from: string
    readonly to: string

    private _layer: Polyline | undefined

    get layer(): Polyline | undefined {
        if (this._layer == undefined) {
            const from = TerritoryLayer.elements.get(this.from)

            if (from == undefined) {
                console.error(`Failed to locate territory ${this.from}`)
                return undefined
            }

            const to = TerritoryLayer.elements.get(this.to)

            if (to == undefined) {
                console.error(`Failed to locate territory ${this.to}`)
                return undefined
            }

            this._layer = L.polyline([
                Leaflet.coordsOf(
                    from.center
                ),
                Leaflet.coordsOf(
                    to.center
                ),
            ], {
                color: "#BFBFBF",
                opacity: 1,
                fillColor: "#000000",
                fillOpacity: 0.25,
                weight: 1.25,
            })
        }

        return this._layer!
    }

    constructor(from: string, to: string) {
        if (from.localeCompare(to) == 1) {
            this.from = from
            this.to = to
        } else {
            this.from = to
            this.to = from
        }
    }

    addTo(object: L.Map | L.LayerGroup): void {
        this.layer?.addTo(object)
    }

    removeFrom(object: L.Map | L.LayerGroup): void {
        if (object instanceof L.Map)
            this.layer?.removeFrom(object)
        else if (this.layer != undefined)
            object.removeLayer(this.layer!)
    }
}