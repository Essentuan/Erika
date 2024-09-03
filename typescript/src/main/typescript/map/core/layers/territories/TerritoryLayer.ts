import * as L from 'leaflet'
import {LayerType} from '../../api/LayerType'
import {TerritoryElement} from "./TerritoryElement";
import {TerritoryList} from "../../../api/territories/TerritoryList";
import {HTMLProvider} from "../../api/HTMLProvider";
import {Leaflet} from "../../Leaflet";
import {DefaultTooltip} from './DefaultTooltip';
import {asSequence} from 'sequency'
import {LinkLayer} from "./LinkLayer";

export class TerritoryLayerType implements LayerType {
    private readonly layer = L.layerGroup<L.Rectangle>()
    readonly elements = new Map<string, TerritoryElement>()

    private tiles: string | undefined
    timestamp: number | undefined

    tooltip: HTMLProvider<TerritoryElement> = DefaultTooltip

    private constructor() {

    }

    update(list: TerritoryList) {
        this.timestamp = list.timestamp

        if (list.tiles.id != this.tiles) {
            this.layer.clearLayers()
            LinkLayer.clear()
            this.elements.clear()

            this.tiles = list.tiles.id
        }

        const added = new Set<String>()

        asSequence(Object.keys(list.territories))
            .plus(asSequence(this.elements.keys()))
            .distinct()
            .forEach(it => {
                if (!this.elements.has(it)) {
                    added.add(it)

                    this.elements.set(it, new TerritoryElement(list.territories[it], this.tooltip))
                } else if (!list.territories.hasOwnProperty(it)) {
                    this.elements.get(it)?.removeFrom(this.layer)
                    this.elements.delete(it)
                } else {
                    this.elements.get(it)!.territory = list.territories[it]
                }
            })

        for (const [name, territory] of this.elements.entries()) {
            territory.redraw(this.tooltip)

            if (added.has(name))
                territory.addTo(this.layer)
        }

        for (const territory of this.elements.values())
            territory.addLinks()

        setTimeout(() => {
            Leaflet.shuffle()
        }, 50)
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

    protected readonly queue = new Array<TerritoryElement>()

    poll() {
        if (TerritoryLayer.queue.length != 0) {
            const queue = TerritoryLayer.queue

            for (let i = 0; i < 5; i++)
                queue.shift()?.style()

            setTimeout(TerritoryLayer.poll, 0);
        } else {
            setTimeout(TerritoryLayer.poll, 50);
        }
    }

    animate() {
        for (const territory of TerritoryLayer.elements.values())
            TerritoryLayer.queue.push(territory)

        setTimeout(TerritoryLayer.animate, 500)
    }

    static INSTANCE = new TerritoryLayerType()
}

let TerritoryLayer = TerritoryLayerType.INSTANCE

export {TerritoryLayer}