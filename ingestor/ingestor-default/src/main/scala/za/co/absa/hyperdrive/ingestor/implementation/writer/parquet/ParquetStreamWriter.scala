/*
 *  Copyright 2019 ABSA Group Limited
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package za.co.absa.hyperdrive.ingestor.implementation.writer.parquet

import org.apache.commons.lang3.StringUtils
import org.apache.hadoop.conf.Configuration
import org.apache.spark.sql.streaming.{DataStreamWriter, OutputMode, StreamingQuery, Trigger}
import org.apache.spark.sql.{DataFrame, Row}
import za.co.absa.hyperdrive.ingestor.api.manager.OffsetManager
import za.co.absa.hyperdrive.ingestor.api.writer.StreamWriter

private[writer] class ParquetStreamWriter(destination: String, val extraConfOptions: Option[Map[String,String]]) extends StreamWriter(destination) {

  if (StringUtils.isBlank(destination)) {
    throw new IllegalArgumentException(s"Invalid PARQUET destination: '$destination'")
  }

  def write(dataFrame: DataFrame, offsetManager: OffsetManager): StreamingQuery = {
    if (dataFrame == null) {
      throw new IllegalArgumentException("Null DataFrame.")
    }

    if (offsetManager == null) {
      throw new IllegalArgumentException("Null OffsetManager instance.")
    }

    val outStream = getOutStream(dataFrame)

    val streamWithOptions = addOptions(outStream, extraConfOptions)

    val streamWithOptionsAndOffset = configureOffsets(streamWithOptions, offsetManager, dataFrame.sparkSession.sparkContext.hadoopConfiguration)

    streamWithOptionsAndOffset.start(destination)
  }

  private def getOutStream(dataFrame: DataFrame): DataStreamWriter[Row] = {
    dataFrame
      .writeStream
      .trigger(Trigger.Once())
      .format(source = "parquet")
      .outputMode(OutputMode.Append())
  }

  private def addOptions(outStream: DataStreamWriter[Row], extraConfOptions: Option[Map[String,String]]): DataStreamWriter[Row] = {
    extraConfOptions match {
      case Some(options) => options.foldLeft(outStream) {
        case (previousOutStream, (optionKey, optionValue)) => previousOutStream.option(optionKey, optionValue)
      }
      case None => outStream
    }
  }

  private def configureOffsets(outStream: DataStreamWriter[Row], offsetManager: OffsetManager, configuration: Configuration): DataStreamWriter[Row] = offsetManager.configureOffsets(outStream, configuration)
}