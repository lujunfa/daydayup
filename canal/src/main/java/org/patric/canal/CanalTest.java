package org.patric.canal;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.Collectors;
 
import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry.Column;
import com.alibaba.otter.canal.protocol.CanalEntry.Entry;
import com.alibaba.otter.canal.protocol.CanalEntry.EntryType;
import com.alibaba.otter.canal.protocol.CanalEntry.EventType;
import com.alibaba.otter.canal.protocol.CanalEntry.RowChange;
import com.alibaba.otter.canal.protocol.CanalEntry.RowData;
import com.alibaba.otter.canal.protocol.Message;
 
public class CanalTest {
 
    public static void main(String[] args) throws Exception {
         
        CanalConnector connector = CanalConnectors.newSingleConnector(new InetSocketAddress("127.0.0.1", 11111), "example", "", "");
        connector.connect();
        connector.subscribe(".*\\..*");
        connector.rollback();
         
        while (true) {
            Message message = connector.getWithoutAck(100);  // 获取指定数量的数据
            long batchId = message.getId();
            if (batchId == -1 || message.getEntries().isEmpty()) {
                Thread.sleep(1000);
                continue;
            }
           // System.out.println(message.getEntries());
            printEntries(message.getEntries());
            connector.ack(batchId);// 提交确认，消费成功，通知server删除数据
//            connector.rollback(batchId);// 处理失败, 回滚数据，后续重新获取数据
        }
    }
     
    private static void printEntries(List<Entry> entries) throws Exception {
        for (Entry entry : entries) {
            if (entry.getEntryType() != EntryType.ROWDATA) {
                continue;
            }
 
            RowChange rowChange = RowChange.parseFrom(entry.getStoreValue());
             
            EventType eventType = rowChange.getEventType();
            System.out.println(String.format("================> binlog[%s:%s] , name[%s,%s] , eventType : %s",
                    entry.getHeader().getLogfileName(), entry.getHeader().getLogfileOffset(),
                    entry.getHeader().getSchemaName(), entry.getHeader().getTableName(), eventType));
             
            for (RowData rowData : rowChange.getRowDatasList()) {
                switch (rowChange.getEventType()) {
                    case INSERT:
                         System.out.println("INSERT ");
                         printColumns(rowData.getAfterColumnsList());
                         break;
                    case UPDATE:
                        System.out.println("UPDATE ");
                        printColumns(rowData.getAfterColumnsList());
                        break;
                    case DELETE:
                        System.out.println("DELETE ");
                        printColumns(rowData.getBeforeColumnsList());
                        break;
     
                    default:
                        break;
                 }
            }
        }
    }
 
    private static void printColumns(List<Column> columns) {
        for(Column column : columns) {
            System.out.println(column.getName() + " : " + column.getValue() + "    update=" + column.getUpdated());
        }
    }
}