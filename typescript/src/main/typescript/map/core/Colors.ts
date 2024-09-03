import {GuildType} from "../api/guild/GuildType";

function crc32(input: string) {
    for (var a, o = [], c = 0; c < 256; c++) {
        a = c;
        for (var f = 0; f < 8; f++) a = 1 & a ? 3988292384 ^ a >>> 1 : a >>> 1;
        o[c] = a
    }
    for (var n = -1, t = 0; t < input.length; t++) n = n >>> 8 ^ o[255 & (n ^ input.charCodeAt(t))];
    return (-1 ^ n) >>> 0
}

function generate(str: string) {
    let hexcode = crc32(str).toString(16).slice(-6)

    return "#" + (hexcode.length == 6 ? hexcode : hexcode.padEnd(6, "0"));
}

export class ColorManager {
    private readonly colors = new Map<string, string>()

    of(guild: GuildType): string {
        if (guild.tag == "NONE") {
            return "#FFFFFF"
        }

        return this.colors.get(guild.name) || generate(guild.name)
    }

    register(guild: GuildType, color: string) {
        this.colors.set(guild.name, color)
    }
}

let Colors = new ColorManager()

export {Colors}