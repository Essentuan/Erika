import {millisecondsOf} from "typed-duration/dist/lib";

export function timeSince(date: Date): number {
    return Date.now() - date.getTime()
}
