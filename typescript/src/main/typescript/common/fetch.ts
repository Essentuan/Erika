export async function retrieve<T>(url: string): Promise<T | null> {
    let response = await fetch(url)

    if (response.ok)
        return await response.json() as T
    else
        return null
}