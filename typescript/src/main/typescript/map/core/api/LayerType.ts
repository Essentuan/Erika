import * as L from 'leaflet'

export interface LayerType {
    addTo(object: L.Map | L.LayerGroup): void

    removeFrom(object: L.Map | L.LayerGroup): void
}