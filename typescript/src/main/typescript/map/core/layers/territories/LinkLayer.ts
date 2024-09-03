import {LayerType} from "../../api/LayerType";
import * as L from 'leaflet'

export class LinkLayerType implements LayerType {
    private readonly layer = L.layerGroup<L.Polyline>()

    add(line: LayerType): void {
        line.addTo(this.layer)
    }

    remove(line: LayerType): void {
        line.removeFrom(this.layer)
    }

    clear() {
        this.layer.clearLayers()
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

    static INSTANCE = new LinkLayerType()
}

let LinkLayer = LinkLayerType.INSTANCE

export {LinkLayer}
