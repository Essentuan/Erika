export function PascalCase(input: string): string {
    return (" " + input).toLowerCase().replace(/[^a-zA-Z0-9]+(.)/g, function (_, chr) {
        return " " + chr.toUpperCase()
    }).trim();
}