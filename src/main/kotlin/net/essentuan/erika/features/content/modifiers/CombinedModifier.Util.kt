package net.essentuan.erika.features.content.modifiers

val ContentModifier.size: Int
    get() {
        when (this) {
            EmptyContentModifier -> return 0
            is ContentModifier.Element -> return 1
            is CombinedModifier -> {
                var cur: CombinedModifier = this
                var size = 2
                while (true) {
                    cur = cur.left as? CombinedModifier ?: return size
                    size++
                }
            }
        }
    }