import {TerritoryElement} from "../layers/territories/TerritoryElement";
import {HTMLProvider} from "../api/HTMLProvider";
import {PopupContent} from "./PopupContent";

export class PopupManager {
    private _hovered: TerritoryElement | undefined
    content: HTMLProvider<TerritoryElement> = PopupContent

    private readonly popup = document.getElementById("popup")!
    private readonly body = document.getElementById("popup-content")!
    private readonly nameplate = document.getElementById("popup-nameplate")!

    get hovered(): TerritoryElement | undefined {
        return this._hovered
    }

    set hovered(element: TerritoryElement | undefined) {
        this._hovered = element

        this.update()
    }

    private constructor() {
    }

    private update() {
        const hovered = this.hovered

        if (hovered == undefined)
            this.popup.classList.add("hidden")
        else {
            this.popup.classList.remove("hidden")

            this.body.innerHTML = ""
            this.body.appendChild(
                this.content(hovered)
            )

            this.nameplate.innerText = hovered.name
        }
    }

    animate() {
        Popup.update()

        setTimeout(Popup.animate, 250)
    }

    static INSTANCE = new PopupManager()
}

const Popup = PopupManager.INSTANCE

export {Popup}