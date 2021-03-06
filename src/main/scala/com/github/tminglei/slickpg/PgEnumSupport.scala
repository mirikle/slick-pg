package com.github.tminglei.slickpg

import scala.slick.jdbc.JdbcType
import scala.slick.driver.PostgresDriver
import scala.slick.lifted.Column
import scala.reflect.ClassTag
import java.sql.{PreparedStatement, ResultSet}

trait PgEnumSupport extends enums.PgEnumExtensions with array.PgArrayJdbcTypes { driver: PostgresDriver =>
  import PgEnumSupportUtils.sqlName
  
  def createEnumColumnExtensionMethodsBuilder[T <: Enumeration](enumObject: T)(
      implicit tm: JdbcType[enumObject.Value], tm1: JdbcType[List[enumObject.Value]]) = 
    (c: Column[enumObject.Value]) => {
      new EnumColumnExtensionMethods[enumObject.Value, enumObject.Value](c)(tm, tm1)
    }
  def createEnumOptionColumnExtensionMethodsBuilder[T <: Enumeration](enumObject: T)(
      implicit tm: JdbcType[enumObject.Value], tm1: JdbcType[List[enumObject.Value]]) = 
    (c: Column[Option[enumObject.Value]]) => {
      new EnumColumnExtensionMethods[enumObject.Value, Option[enumObject.Value]](c)(tm, tm1)
    }

  //-----------------------------------------------------------------------------------
  
  def createEnumListJdbcType[T <: Enumeration](sqlEnumTypeName: String, enumObject: T, quoteName: Boolean = false)(
             implicit tag: ClassTag[List[enumObject.Value]]): JdbcType[List[enumObject.Value]] = {

    new SimpleArrayJdbcType[enumObject.Value](sqlName(sqlEnumTypeName, quoteName))
      .basedOn[String](tmap = _.toString, tcomap = enumObject.withName(_))
      .to(_.toList)
  }

  def createEnumJdbcType[T <: Enumeration](sqlEnumTypeName: String, enumObject: T, quoteName: Boolean = false)(
             implicit tag: ClassTag[enumObject.Value]): JdbcType[enumObject.Value] = {

    new DriverJdbcType[enumObject.Value] {

      override val classTag: ClassTag[enumObject.Value] = tag

      override def sqlType: Int = java.sql.Types.OTHER

      override def sqlTypeName: String = sqlName(sqlEnumTypeName, quoteName)

      override def getValue(r: ResultSet, idx: Int): enumObject.Value = {
        val value = r.getString(idx)
        if (r.wasNull) null.asInstanceOf[enumObject.Value] else enumObject.withName(value)
      }

      override def setValue(v: enumObject.Value, p: PreparedStatement, idx: Int): Unit = p.setObject(idx, mkPgObject(v), sqlType)

      override def updateValue(v: enumObject.Value, r: ResultSet, idx: Int): Unit = r.updateObject(idx, mkPgObject(v))

      override def hasLiteralForm: Boolean = true

      override def valueToSQLLiteral(v: enumObject.Value) = if (v eq null) "NULL" else s"'$v'"

      ///
      private def mkPgObject(v: enumObject.Value) =
        utils.mkPGobject( (if (quoteName) sqlEnumTypeName else sqlEnumTypeName.toLowerCase), (if (v eq null) null else v.toString) )
    }
  }
}

object PgEnumSupportUtils {
  import scala.slick.jdbc.{StaticQuery => Q, Invoker}

  def sqlName(sqlTypeName: String, quoteName: Boolean) = if (quoteName)
    '"' + sqlTypeName + '"' else sqlTypeName.toLowerCase

  def buildCreateSql[T <: Enumeration](sqlTypeName: String, enumObject: T, quoteName: Boolean = false): Invoker[Int] = {
    // `toStream` to prevent re-ordering after `map(e => s"'${e.toString}'")`
    val enumValuesString = enumObject.values.toStream.map(e => s"'$e'").mkString(",")
    Q[Int] + s"create type ${sqlName(sqlTypeName, quoteName)} as enum ($enumValuesString)"
  }
  
  def buildDropSql(sqlTypeName: String, quoteName: Boolean = false): Invoker[Int] =
    Q[Int] + s"drop type ${sqlName(sqlTypeName, quoteName)}"
}