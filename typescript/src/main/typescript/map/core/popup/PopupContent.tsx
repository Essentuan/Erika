import {JSX} from "../../../common/jsx";
import {Territory} from "../../api/territories/Territory";
import {Resource} from "../../api/territories/Resource";
import {TerritoryElement} from "../layers/territories/TerritoryElement";
import {Rating} from "../../api/territories/Rating";
import {millisecondsOf} from "typed-duration/dist/lib";
import {timeSince} from "../../../common/date";
import duration from 'humanize-duration';
import {PascalCase} from "../../../common/strings";

const DARK_RED = "#aa0000"
const RED = "#ff5555"
const GOLD = "#ffaa00"
const YELLOW = "#ffff55"
const GREEN = "#55ff55"
const DARK_GREEN = "#00aa00"
const AQUA = "#55ffff"
const MAGENTA = "#ff0083"
const GRAY = "#aaaaaa"

const prettyPrint = duration.humanizer({
    language: "shortEn",
    languages: {
        shortEn: {
            y: () => "y",
            mo: () => "mo",
            w: () => "w",
            d: () => "d",
            h: () => "h",
            m: () => "m",
            s: () => "s",
            ms: () => "ms",
        },
    },
    round: true,
});

function resourceColor(resource: string): string {
    switch (resource) {
        case Resource.EMERALDS:
            return GREEN
        case Resource.ORE:
            return "white"
        case Resource.WOOD:
            return GOLD
        case Resource.FISH:
            return AQUA
        case Resource.CROP:
            return YELLOW
        default:
            throw new Error(`Unknown resource ${resource}!`)
    }
}

function treasuryColor(treasury: string): string {
    switch (treasury) {
        case Rating.VERY_HIGH:
            return AQUA
        case Rating.HIGH:
            return RED
        case Rating.MEDIUM:
            return YELLOW
        case Rating.LOW:
            return GREEN
        case Rating.VERY_LOW:
            return DARK_GREEN
        default:
            throw new Error(`Unknown treasury level ${treasury}!`)
    }
}

function defenseColor(defense: string): string {
    switch (defense) {
        case Rating.VERY_HIGH:
            return DARK_RED
        case Rating.HIGH:
            return RED
        case Rating.MEDIUM:
            return YELLOW
        case Rating.LOW:
            return GREEN
        case Rating.VERY_LOW:
            return DARK_GREEN
        default:
            throw new Error(`Unknown defense level ${defense}!`)
    }
}

function content(ctx: TerritoryElement): HTMLElement {
    const rows = new Array<HTMLElement>()

    for (let [resource, prod] of Object.entries(ctx.resources)) {
        const color = resourceColor(resource)

        if (prod.production > 0) {
            rows.push(
                <span style={`color: ${color}`}>
                    <img src={`assets/icons/map/${resource.toLowerCase()}.png`} width={13} height={13}
                         style={"padding-right: 3px"}/>
                    {` +${prod.production} ${PascalCase(resource)} per Hour`}
                </span>
            )

            rows.push(<br/>)
        }

        if (prod.stored > 0) {
            rows.push(
                <span style={`color: ${color}`}>
                    <img src={`assets/icons/map/${resource.toLowerCase()}.png`} width={13} height={13}
                         style={"padding-right: 3px"}/>
                    {` ${prod.stored}/${prod.capacity} ${PascalCase(resource)} stored`}
                </span>
            )

            rows.push(<br/>)
        }
    }

    if (rows.length > 0)
        rows.push(<br/>)

    const treasury = ctx.treasury

    rows.push(
        <span style={`color: ${GRAY}`}>
            ✦ Treasury: 
            <span style={`color: ${treasuryColor(treasury)}`}>
                {" "}{PascalCase(treasury)}
            </span>
        </span>
    )

    rows.push(<br/>)

    rows.push(
        <span style={`color: ${GRAY}`}>
            <i class="fa-solid fa-shield fa-sm"></i>
            {" "}Defense: 
             <span style={`color: ${defenseColor(ctx.defense)}`}>
                {" "}{PascalCase(ctx.defense)}
            </span>
        </span>
    )

    rows.push(<br/>, <br/>)

    return <p>
        <span style={`color: ${MAGENTA}`}>{ctx.owner.name} [{ctx.owner.tag}]</span>
        <br/><br/>
        {rows}
        <span style={`color: ${GRAY}`}>
            Time Held: 
            <span style={`color: ${YELLOW}`}>
                {" "}{
                prettyPrint((timeSince(ctx.acquiredAt)), {
                    serialComma: false,
                    delimiter: " ",
                    spacer: ""
                })
            }
            </span>
        </span>
    </p>
}

export {content as PopupContent}