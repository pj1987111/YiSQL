package com.zhy.yisql.runner

import com.zhy.yisql.common.utils.json.JsonUtils
import org.apache.spark.sql.SparkSession

import scala.collection.mutable

/**
  *  \* Created with IntelliJ IDEA.
  *  \* User: hongyi.zhou
  *  \* Date: 2021-02-02
  *  \* Time: 21:38
  *  \* Description: 
  *  \*/
class RunScriptExecutor(_params: Map[String, String]) {
    private val extraParams = mutable.HashMap[String, String]()
    private var _autoClean = false

    def sql(sql: String) = {
        extraParams += ("sql" -> sql)
        this
    }

    def simpleExecute(): (Int, String) = {
        simpleExecute(getSimpleSession)
    }

    def simpleExecute(sparkSession: SparkSession): (Int, String) = {
        val silence = paramAsBoolean("silence", false)
        val includeSchema = param("includeSchema", "false").toBoolean
        var outputResult: String = if (includeSchema) "{}" else "[]"

        try {
//            val jobInfo = JobManager.getJobInfo(
//                param("owner"), param("jobType", MLSQLJobType.SCRIPT), param("jobName"), param("sql"),
//                paramAsLong("timeout", -1L)
//            )

            val listener = createScriptSQLExecListener(sparkSession)
            JobManager.run(() => {
                ScriptSQLExec.parse(param("sql"), listener)
            })
            if (!silence)
                outputResult = getScriptResult(listener, sparkSession)
        } finally {
            //            sparkSession.close()
        }
        (200, outputResult)
    }

    private def getScriptResult(context: ScriptSQLExecListener, sparkSession: SparkSession): String = {
        val result = new StringBuffer()
        val includeSchema = param("includeSchema", "false").toBoolean
        val fetchType = param("fetchType", "collect")
        if (includeSchema) {
            result.append("{")
        }
        context.getLastSelectTable() match {
            case Some(table) =>
                // result hook
                var df = sparkSession.table(table)
                if (includeSchema) {
                    result.append(s""" "schema":${df.schema.json},"data": """)
                }

                val outputSize = paramAsInt("outputSize", 5000)
                val jsonDF = sparkSession.sql(s"select * from $table limit " + outputSize).toJSON
                val scriptJsonStringResult = fetchType match {
                    case "collect" => jsonDF.collect().mkString(",")
                    case "take" => sparkSession.table(table).toJSON.take(outputSize).mkString(",")
                }
                result.append("[" + scriptJsonStringResult + "]")
            case None => result.append("[]")
        }
        if (includeSchema) {
            result.append("}")
        }
        result.toString
    }

    private def createScriptSQLExecListener(sparkSession: SparkSession) = {
        val allPathPrefix = JsonUtils.fromJson[Map[String, String]](param("allPathPrefix", "{}"))
        val defaultPathPrefix = param("defaultPathPrefix", "")
        val pathPrefix = new PathPrefix(defaultPathPrefix, allPathPrefix)

        val context = new ScriptSQLExecListener(sparkSession, pathPrefix)
        val ownerOption = if (params.contains("owner")) Some(param("owner")) else None
        val userDefineParams = params.filter(f => f._1.startsWith("context.")).map(f => (f._1.substring("context.".length), f._2))

        ScriptSQLExec.setContext(ExecuteContext(context, param("owner"),
            userDefineParams ++ Map("__PARAMS__" -> JsonUtils.toJson(params()))
        ))
        context.addEnv("HOME", pathPrefix.pathPrefix(None))
        context.addEnv("OWNER", ownerOption.getOrElse("anonymous"))
        context
    }

    private def param(str: String) = {
        params.getOrElse(str, null)
    }

    private def param(str: String, defaultV: String) = {
        params.getOrElse(str, defaultV)
    }

    private def paramAsBoolean(str: String, defaultV: Boolean) = {
        params.getOrElse(str, defaultV.toString).toBoolean
    }

    private def paramAsLong(str: String, defaultV: Long) = {
        params.getOrElse(str, defaultV.toString).toLong
    }

    private def paramAsInt(str: String, defaultV: Int) = {
        params.getOrElse(str, defaultV.toString).toInt
    }

    private def hasParam(str: String) = {
        params.contains(str)
    }

    private def params() = {
        _params ++ extraParams
    }

    //    def getSession = {
    //
    //        val session = if (paramAsBoolean("sessionPerUser", false)) {
    //            PlatformManager.getRuntime.asInstanceOf[SparkRuntime].getSession(param("owner", "admin"))
    //        } else {
    //            PlatformManager.getRuntime.asInstanceOf[SparkRuntime].sparkSession
    //        }
    //
    //        if (paramAsBoolean("sessionPerRequest", false)) {
    //            session.cloneSession
    //        } else {
    //            session
    //        }
    //    }

    def getSimpleSession = {
        SparkSession
                .builder()
                .master("local[*]")
                .appName("RunScriptExecutor")
                .enableHiveSupport()
                .getOrCreate()
    }
}
