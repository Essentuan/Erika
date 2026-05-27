import {TerritoryList} from "./api/territories/TerritoryList";
import {Leaflet} from "./core/Leaflet";
import {AthenaGuild, AthenaGuildList} from "./api/athena/AthenaGuild";
import {Colors} from "./core/Colors";
import {retrieve} from "../common/fetch";
import {TerritoryLayer} from "./core/layers/territories/TerritoryLayer";
import {Popup} from "./core/popup/Popup"
import tinycolor from 'tinycolor2'

interface IdObj {
    timestamp: number
}

function colorOf(int: number) {
    int >>>= 0;

    return tinycolor({
        r: (int & 0xFF0000) >>> 16,
        g: (int & 0xFF00) >>> 8,
        b: int & 0xFF
    })
}

async function main() {
    let [territories, guilds] = await Promise.all([
        retrieve<TerritoryList>("/api/territories"),
        retrieve<AthenaGuildList>("/api/guildList")
    ]);

    if (guilds != null) {
        for (let guild of guilds.guilds) {
            if (guild.color != undefined && guild.color.length > 0) {
                Colors.register({
                    name: guild._id,
                    uuid: "",
                    tag: ""
                }, colorOf(Number.parseInt(guild.color.substring(1), 16)).toHexString())
            }
        }
    }

    if (territories != null)
        Leaflet.update(territories)

    TerritoryLayer.animate()
    TerritoryLayer.poll()

    Popup.animate()

    update()
}

async function update() {
    try {
        const id = await retrieve<IdObj>("/api/territories/id")

        if (id != null && id.timestamp != TerritoryLayer.timestamp) {
            const territories = await retrieve<TerritoryList>("/api/territories")

            if (territories != null)
                Leaflet.update(territories)
        }
    } catch (e) {
        console.error(`Error updating territories`, e)
    }

    setTimeout(update, 100)
}

main()