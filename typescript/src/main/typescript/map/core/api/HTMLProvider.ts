export interface HTMLProvider<CTX> {
    (ctx: CTX): HTMLElement
}