package it.nerdammer.spark.hbase

import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.mapreduce.TableInputFormat
import org.apache.spark.{Logging, SparkContext}
import org.apache.spark.rdd.{NewHadoopRDD, RDD}

import scala.reflect.ClassTag

/**
 * Created by Nicola Ferraro on 17/01/15.
 */
case class HBaseReaderBuilder [R: ClassTag] private[hbase] (
      @transient sc: SparkContext,
      table: String,
      columnFamily: Option[String] = None,
      columns: Iterable[String] = Seq.empty,
      startRow: Option[String] = None,
      stopRow: Option[String] = None
      )
      (implicit mapper: FieldMapper[R]) extends Serializable {


    def select(columns: String*): HBaseReaderBuilder[R] = {
      require(this.columns.isEmpty, "Columns have already been set")
      require(columns.nonEmpty, "You should provide at least one column")

      this.copy(columns = columns)
    }

    def inColumnFamily(columnFamily: String) = {
      require(this.columnFamily.isEmpty, "Default column family has already been set")
      require(columnFamily.nonEmpty, "Invalid column family provided")

      this.copy(columnFamily = Some(columnFamily))
    }

    def withStartRow(startRow: String) = {
      require(startRow.nonEmpty, s"Invalid start row '${startRow}'")
      require(this.startRow.isEmpty, "Start row has already been set")

      this.copy(startRow = Some(startRow))
    }

    def withStopRow(stopRow: String) = {
      require(stopRow.nonEmpty, s"Invalid stop row '${stopRow}'")
      require(this.stopRow.isEmpty, "Stop row has already been set")

      this.copy(stopRow = Some(stopRow))
    }

}


trait HBaseReaderBuilderConversions extends Serializable {

  implicit def toSimpleHBaseRDD[R: ClassTag](builder: HBaseReaderBuilder[R])(implicit mapper: FieldMapper[R]): HBaseSimpleRDD[R] = {
    val hbaseConfig = HBaseSparkConf.fromSparkConf(builder.sc.getConf).createHadoopBaseConfig()

    hbaseConfig.set(TableInputFormat.INPUT_TABLE, builder.table)

    val columns =
      if(builder.columnFamily.isEmpty) builder.columns
      else builder.columns map (c => {
        if(c.indexOf(':') >= 0) c
        else builder.columnFamily.get + ':' + c
      })

    if(columns.exists(c => c.indexOf(':') < 0))
      throw new IllegalArgumentException("You must specify the default column family or use the fully qualified name of the columns. Eg. 'cf1:col1'")

    if(columns.nonEmpty) {
      hbaseConfig.set(TableInputFormat.SCAN_COLUMNS, columns.mkString(" "))
    }

    if(builder.startRow.nonEmpty) {
      hbaseConfig.set(TableInputFormat.SCAN_ROW_START, builder.startRow.get)
    }

    if(builder.stopRow.nonEmpty) {
      hbaseConfig.set(TableInputFormat.SCAN_ROW_STOP, builder.stopRow.get)
    }

    val rdd = builder.sc.newAPIHadoopRDD(hbaseConfig, classOf[TableInputFormat],
      classOf[ImmutableBytesWritable], classOf[Result])
      .asInstanceOf[NewHadoopRDD[ImmutableBytesWritable, Result]]

    new HBaseSimpleRDD[R](rdd)
  }


}
