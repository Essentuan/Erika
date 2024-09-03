import {IPosition} from "./IPosition";

export interface IRegion {
    readonly start: IPosition
    readonly end: IPosition
}

export class Region implements IRegion {
    constructor(readonly start: IPosition, readonly end: IPosition) {

    }
}