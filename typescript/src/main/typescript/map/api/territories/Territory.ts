import {GuildType} from "../guild/GuildType";
import {IRegion} from "../Region";
import {Production} from "./Production";
import {Resource} from "./Resource";
import {Rating} from "./Rating";

export interface Territory {
    readonly name: string
    readonly acquired: number
    readonly owner: GuildType
    readonly location: IRegion
    readonly resources: Record<Resource, Production>
    readonly defense: Rating,
    readonly hq: boolean
    readonly connections: string[]
}