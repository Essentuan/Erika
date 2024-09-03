import {Territory} from "../../../api/territories/Territory";
import {LayerType} from "../../api/LayerType";
import * as L from 'leaflet'
import {Leaflet} from "../../Leaflet";
import {Colors} from "../../Colors";
import {GuildType} from "../../../api/guild/GuildType";
import {Region} from "../../../api/Region";
import {Position} from "../../../api/IPosition";
import {HTMLProvider} from "../../api/HTMLProvider";
import {Rating} from "../../../api/territories/Rating";
import {Resource} from "../../../api/territories/Resource";
import {Production} from "../../../api/territories/Production";
import tinycolor from "tinycolor2"
import {daysOf, hoursOf, millisecondsFrom, minutesOf} from "typed-duration/dist/lib";
import {timeSince} from "../../../../common/date";
import {Link} from "./Link";
import {LinkLayer} from "./LinkLayer";
import {Popup} from "../../popup/Popup"

let TEN_MINUTES = millisecondsFrom(minutesOf(10))

const TWELVE_DAYS = millisecondsFrom(daysOf(12))
const FIVE_DAYS = millisecondsFrom(daysOf(5))
const ONE_DAY = millisecondsFrom(daysOf(1))
const ONE_HOUR = millisecondsFrom(hoursOf(1))

export class TerritoryElement implements Territory, LayerType {
    private readonly layer: L.Rectangle
    private links = new Array<Link>()

    constructor(private backing: Territory, tooltip: HTMLProvider<TerritoryElement>) {
        this.layer = L.rectangle(this.bounds)
        this.style()

        this.layer.on("mouseover", _ => {
            Popup.hovered = this
        })

        this.layer.on("mouseout", _ => {
            Popup.hovered = undefined
        })
    }

    get territory(): Territory {
        return this.backing
    }

    set territory(value: Territory) {
        const before = new Set(this.backing.connections)

        if (before.size != value.connections.length || !value.connections.every(it => before.has(it))) {
            for (const link of this.links)
                LinkLayer.remove(link)

            this.links = new Array<Link>()
        }

        this.backing = value
    }

    get name(): string {
        return this.backing.name
    }

    get acquired(): number {
        return this.backing.acquired
    }

    get acquiredAt(): Date {
        return new Date(this.backing.acquired)
    }

    get owner(): GuildType {
        return this.backing.owner
    }

    get location(): Region {
        return this.backing.location
    }

    get resources(): Record<Resource, Production> {
        return this.backing.resources
    }

    get defense(): Rating {
        return this.backing.defense
    }

    get treasury(): Rating {
        const held = timeSince(this.acquiredAt)

        if (held >= TWELVE_DAYS)
            return Rating.VERY_HIGH
        else if (held >= FIVE_DAYS)
            return Rating.HIGH
        else if (held >= ONE_DAY)
            return Rating.MEDIUM
        else if (held >= ONE_HOUR)
            return Rating.LOW
        else
            return Rating.VERY_LOW
    }

    get hq(): boolean {
        return this.backing.hq
    }

    get connections(): string[] {
        return this.backing.connections
    }

    get center(): Position {
        return new Position(
            this.location.start.x + this.width / 2,
            this.location.start.z + this.height / 2
        )
    }

    get width(): number {
        return this.location.end.x - this.location.start.x
    }

    get height(): number {
        return this.location.end.z - this.location.start.z
    }

    get bounds(): L.LatLngBounds {
        return L.latLngBounds(
            Leaflet.coordsOf(
                new Position(
                    this.location.start.x,
                    this.location.end.z
                )
            ),
            Leaflet.coordsOf(
                new Position(
                    this.location.end.x,
                    this.location.start.z
                )
            ),
        )
    }

    style() {
        let options = this.layer.options
        let color = Colors.of(this.owner)

        options.stroke = true
        options.color = color
        options.weight = 2
        options.opacity = 1
        options.lineCap = "round"
        options.lineJoin = "round"

        options.fill = true
        options.fillOpacity = 0.35
        options.fillRule = "evenodd"

        if (timeSince(this.acquiredAt) < TEN_MINUTES) {
            options.fillColor = tinycolor({
                h: Math.floor((timeSince(this.acquiredAt) / TEN_MINUTES) * 122),
                s: 75,
                v: 100
            }).toRgbString()

            options.dashArray = "7"
        } else {
            options.fillColor = color
            options.dashArray = "0"
        }

        this.layer.setStyle(options)
    }

    addLinks() {
        if (this.links.length == 0 && this.connections.length != 0) {
            for (const connection of this.connections) {
                const link = new Link(this.name, connection)

                if (link.from == this.name) {
                    this.links.push(link)
                    LinkLayer.add(link)
                }
            }
        }
    }

    redraw(tooltip: HTMLProvider<TerritoryElement>) {
        this.layer.unbindTooltip()
        this.layer.bindTooltip(tooltip(this), {
            permanent: true,
            direction: "center",
            className: "bg-transparent border-none shadow-none"
        })

        this.style()
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

export {TEN_MINUTES}