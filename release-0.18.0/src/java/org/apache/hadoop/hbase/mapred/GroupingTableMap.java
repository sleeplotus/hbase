/**
 * Copyright 2007 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.mapred;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Map;

import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.io.Cell;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.io.RowResult;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;


/**
 * Extract grouping columns from input record
 */
public class GroupingTableMap extends TableMap<ImmutableBytesWritable,RowResult> {

  /**
   * JobConf parameter to specify the columns used to produce the key passed to 
   * collect from the map phase
   */
  public static final String GROUP_COLUMNS =
    "hbase.mapred.groupingtablemap.columns";
  
  protected byte [][] m_columns;

  /**
   * Use this before submitting a TableMap job. It will appropriately set up the
   * JobConf.
   *
   * @param table table to be processed
   * @param columns space separated list of columns to fetch
   * @param groupColumns space separated list of columns used to form the key
   * used in collect
   * @param mapper map class
   * @param job job configuration object
   */
  @SuppressWarnings("unchecked")
  public static void initJob(String table, String columns, String groupColumns, 
    Class<? extends TableMap> mapper, JobConf job) {
    
    initJob(table, columns, mapper, ImmutableBytesWritable.class, RowResult.class, job);
    job.set(GROUP_COLUMNS, groupColumns);
  }

  @Override
  public void configure(JobConf job) {
    super.configure(job);
    String[] cols = job.get(GROUP_COLUMNS, "").split(" ");
    m_columns = new byte[cols.length][];
    for(int i = 0; i < cols.length; i++) {
      m_columns[i] = Bytes.toBytes(cols[i]);
    }
  }

  /**
   * Extract the grouping columns from value to construct a new key.
   * 
   * Pass the new key and value to reduce.
   * If any of the grouping columns are not found in the value, the record is skipped.
   */
  @Override
  public void map(@SuppressWarnings("unused") ImmutableBytesWritable key,
      RowResult value, OutputCollector<ImmutableBytesWritable,RowResult> output,
      @SuppressWarnings("unused") Reporter reporter) throws IOException {
    
    byte[][] keyVals = extractKeyValues(value);
    if(keyVals != null) {
      ImmutableBytesWritable tKey = createGroupKey(keyVals);
      output.collect(tKey, value);
    }
  }

  /**
   * Extract columns values from the current record. This method returns
   * null if any of the columns are not found.
   * 
   * Override this method if you want to deal with nulls differently.
   * 
   * @param r
   * @return array of byte values
   */
  protected byte[][] extractKeyValues(RowResult r) {
    byte[][] keyVals = null;
    ArrayList<byte[]> foundList = new ArrayList<byte[]>();
    int numCols = m_columns.length;
    if(numCols > 0) {
      for (Map.Entry<byte [], Cell> e: r.entrySet()) {
        byte [] column = e.getKey();
        for (int i = 0; i < numCols; i++) {
          if (Bytes.equals(column, m_columns[i])) {
            foundList.add(e.getValue().getValue());
            break;
          }
        }
      }
      if(foundList.size() == numCols) {
        keyVals = foundList.toArray(new byte[numCols][]);
      }
    }
    return keyVals;
  }

  /**
   * Create a key by concatenating multiple column values. 
   * Override this function in order to produce different types of keys.
   * 
   * @param vals
   * @return key generated by concatenating multiple column values
   */
  protected ImmutableBytesWritable createGroupKey(byte[][] vals) {
    if(vals == null) {
      return null;
    }
    StringBuilder sb =  new StringBuilder();
    for(int i = 0; i < vals.length; i++) {
      if(i > 0) {
        sb.append(" ");
      }
      try {
        sb.append(new String(vals[i], HConstants.UTF8_ENCODING));
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }
    return new ImmutableBytesWritable(Bytes.toBytes(sb.toString()));
  }
}
