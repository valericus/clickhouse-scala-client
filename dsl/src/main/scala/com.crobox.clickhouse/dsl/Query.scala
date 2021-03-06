package com.crobox.clickhouse.dsl

import com.crobox.clickhouse.dsl.TableColumn.AnyTableColumn
import com.dongxiguo.fastring.Fastring.Implicits._

import scala.util.Try

trait Table {
  val name: String
  val columns: Seq[NativeColumn[_]]
}

trait Query {
  val internalQuery: InternalQuery
}

case class Limit(size: Long = 100, offset: Long = 0)

trait OrderingDirection

case object ASC extends OrderingDirection

case object DESC extends OrderingDirection

sealed case class InternalQuery(select: Option[SelectQuery] = None,
                                from: Option[FromQuery] = None,
                                where: Option[Comparison] = None,
                                groupBy: Seq[AnyTableColumn] = Seq.empty,
                                having: Option[Comparison] = None,
                                join: Option[JoinQuery] = None,
                                orderBy: Seq[(AnyTableColumn, OrderingDirection)] = Seq.empty,
                                limit: Option[Limit] = None) {

  def isValid = {
    val validGroupBy = groupBy.isEmpty && having.isEmpty || groupBy.nonEmpty

    select.isDefined && validGroupBy
  }

  def isPartial = !isValid

  /**
    * Merge with another InternalQuery, any conflict on query parts between the 2 joins will be resolved by
    * preferring the left querypart over the right one.
    *
    * @param other The right part to merge with this InternalQuery
    * @return A merge of this and other InternalQuery
    */
  def :+>(other: InternalQuery): InternalQuery =
    InternalQuery(
      select.orElse(other.select),
      from.orElse(other.from),
      where.orElse(other.where),
      if (groupBy.nonEmpty) groupBy else other.groupBy,
      having.orElse(other.having),
      join.orElse(other.join),
      if (orderBy.nonEmpty) orderBy else other.orderBy,
      limit.orElse(other.limit)
    )

  /**
    * Right associative version of the merge (:+>) operator.
    *
    * @param other The left part to merge with this InternalQuery
    * @return A merge of this and other OperationalQuery
    */
  def <+:(other: InternalQuery): InternalQuery = :+>(other)


  /**
    * Tries to merge this InternalQuery with other
    *
    * @param other The Query parts to merge against
    * @return A Success on merge without conflict, or Failure of IllegalArgumentException otherwise.
    */
  def +(other: InternalQuery): Try[InternalQuery] = Try{
    (0 until productArity).foreach(id => {
      require(
        (productElement(id), other.productElement(id)) match {
          case (ts: Option[_], tt: Option[_]) =>
            ts.isEmpty || tt.isEmpty
          case (ts: Iterable[_], tt: Iterable[_]) =>
            ts.isEmpty || tt.isEmpty
        },
        fast"Conflicting parts ${productElement(id)} and ${other.productElement(id)}"
      )
    })
    :+>(other)
  }
}
