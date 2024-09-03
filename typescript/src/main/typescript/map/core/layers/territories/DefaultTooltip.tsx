import {TerritoryElement} from "./TerritoryElement";
import {JSX} from "../../../../common/jsx/";
import {Resource} from "../../../api/territories/Resource";
import {Colors} from "../../Colors";

function tooltip(ctx: TerritoryElement): HTMLElement {
    if (ctx.hq) {
        return <div style={`width: ${16 * 2}; height: ${13 * 2}`}>
            <img src={"/assets/icons/map/headquarters.png"} alt={"guild headquarter"} width={16 * 2} height={13 * 2}
                 style={"image-rendering: pixelated"}/>
        </div>
    }

    const rows = new Array<HTMLElement>()

    for (let [resource, prod] of Object.entries(ctx.resources)) {
        switch (resource) {
            case Resource.EMERALDS: {
                if (prod.base > 9000)
                    rows.push(<img src={"/assets/icons/map/emeralds.png"} alt={"emeralds"} class={"h-4 w-4"}/>)

                break
            }

            default: {
                for (let i = 0; i < Math.ceil((prod.base / 900) / 4); i++) {
                    rows.push(
                        <img src={`/assets/icons/map/${resource.toLowerCase()}.png`} alt={"emeralds"}
                             class={"h-4 w-4"}/>
                    )
                }
            }
        }
    }

    return <div class={"w-10 flair"}>
        <span class={"block text-center tag text-outline"} style={`color:${Colors.of(ctx.owner)}`}>
            {ctx.owner.tag}
        </span>
        <span class={`flex justify-center${ctx.width < 125 ? " flex-wrap" : ""}`}>
            {rows}
        </span>
    </div>
}

export {tooltip as DefaultTooltip}