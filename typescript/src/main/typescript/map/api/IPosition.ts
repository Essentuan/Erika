export interface IPosition {
    readonly x: number
    readonly z: number
}

export class Position implements IPosition {
    readonly x: number
    readonly z: number

    constructor(pos: IPosition)
    constructor(x: number, z: number);
    constructor(x: number | IPosition, z?: number) {
        if (Number.isFinite(x)) {
            this.x = x as number
            this.z = z!!
        } else {
            this.x = (x as IPosition).x
            this.z = (x as IPosition).z
        }
    }
}