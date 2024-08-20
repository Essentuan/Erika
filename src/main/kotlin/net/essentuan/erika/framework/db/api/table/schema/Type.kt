package net.essentuan.erika.framework.db.api.table.schema

interface Type : Tag {
    val value: Class<*>
}