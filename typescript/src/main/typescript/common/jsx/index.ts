import {Any} from "sequency/lib/any";

/**
 * A helper function that ensures we won't work with null values
 */
function nonNull(val: any, fallback: any) {
    return Boolean(val) ? val : fallback
}

/**
 * How do we handle children. Children can either be:
 * 1. Calls to DOMcreateElement, returns a Node
 * 2. Text content, returns a Text
 *
 * Both can be appended to other nodes.
 */
function DOMparseChildren(children: any): Array<any> {
    return children.map((child: any) => {
        if (typeof child === 'string') {
            return document.createTextNode(child);
        }
        return child;
    })
}

/**
 * How do we handle regular nodes.
 * 1. We create an element
 * 2. We apply all properties from JSX to this DOM node
 * 3. If available, we append all children.
 */
function DOMparseNode(element: any, properties: any, children: any) {
    const el = document.createElement(element);
    Object.keys(nonNull(properties, {})).forEach(key => {
        switch (key) {
            case "class": {
                el.className = properties["class"]

                break;
            }

            default: {
                el[key] = properties[key];
            }
        }
    })

    append(DOMparseChildren(children), el)

    return el;
}

function append(iterable: Iterable<any>, el: Element) {
    for (let child of iterable) {
        if (child instanceof Node)
            el.appendChild(child)
        else if (typeof child[Symbol.iterator] === 'function')
            append(child as Iterable<any>, el)
    }
}

/**
 * Our entry function.
 * 1. Is the element a function, than it's a functional component.
 *    We call this function (pass props and children of course)
 *    and return the result. We expect a return value of type Node
 * 2. If the element is a string, we parse a regular node
 */
function DOMcreateElement(element: any, properties: any, ...children: any) {
    if (typeof element === 'function') {
        return element({
            ...nonNull(properties, {}),
            children
        });
    }
    return DOMparseNode(element, properties, children);
}


export const JSX = {
    createElement(element: any, properties: any, ...children: any): HTMLElement {
        if (typeof element === 'function') {
            return element({
                ...nonNull(properties, {}),
                children
            });
        }
        return DOMparseNode(element, properties, children);
    }
}