package com.zhy.yisql.core.datasource.impl

import com.zhy.yisql.core.datasource.{BaseMergeSource, DataSinkConfig, DataSourceConfig}
import org.apache.spark.sql.streaming.DataStreamReader
import org.apache.spark.sql.{DataFrame, DataFrameReader, DataFrameWriter, Row}

/**
  *  \* Created with IntelliJ IDEA.
  *  \* User: hongyi.zhou
  *  \* Date: 2021-02-08
  *  \* Time: 10:24
  *  \* Description: 
  *  \*/
class YiSQLHbase extends BaseMergeSource {

  override def bLoad(reader: DataFrameReader, config: DataSourceConfig): DataFrame = {
    var namespace = ""
    val dbTable = config.path

    if (config.config.contains("namespace")) {
      namespace = config.config("namespace")
    }

    val inputTableName = if (namespace == "") dbTable else s"${namespace}:${dbTable}"

    reader.option("inputTableName", inputTableName)
    val format = config.config.getOrElse("implClass", fullFormat)
    //load configs should overwrite connect configs
    reader.options(config.config)
    reader.format(format).load()
  }

  override def bSave(writer: DataFrameWriter[Row], config: DataSinkConfig): Any = {
    var namespace = ""
    val dbtable = config.path

    if (config.config.contains("namespace")) {
      namespace = config.config("namespace")
    }

    val outputTableName = if (namespace == "") dbtable else s"${namespace}:${dbtable}"

    writer.mode(config.mode)
    writer.option("outputTableName", outputTableName)
    writer.options(config.config)
    config.config.get("partitionByCol").map { item =>
      writer.partitionBy(item.split(","): _*)
    }
    writer.format(config.config.getOrElse("implClass", fullFormat)).save(dbtable)
  }

  override def sLoad(reader: DataStreamReader, config: DataSourceConfig): DataFrame = {
    throw new RuntimeException(s"stream load is not support with ${shortFormat} ")
  }

  override def skipFormat: Boolean = true

  override def fullFormat: String = "org.apache.spark.sql.execution.datasources.hbase"

  override def shortFormat: String = "hbase"

  override def dbSplitter: String = "/"

}
