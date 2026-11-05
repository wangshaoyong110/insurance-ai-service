package com.insure.ai.modules.service.support;

import com.insure.ai.modules.service.entity.ServiceTicket;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工单 Excel 导出单测
 * <p>重点验证三件事:表头齐全、行数与数据一致、手机号在导出文件里已脱敏
 * (导出文件常被转发,明文手机号等于把客户信息带出系统)。</p>
 *
 * @author shaoyong.wang
 */
@DisplayName("工单 Excel 导出")
class TicketExcelExporterTest {

    @Test
    @DisplayName("导出的表头、数据行与手机号脱敏都正确")
    void exportShouldWriteHeaderAndMaskedRows() throws IOException {
        ServiceTicket ticket = new ServiceTicket();
        ticket.setTicketNo("TK202611050001");
        ticket.setCustomerName("张三");
        ticket.setCustomerPhone("13900000001");
        ticket.setQuestion("重大疾病保险等待期多久?");
        ticket.setReason("COMPLEX");
        ticket.setPriority(2);
        ticket.setStatus(2);
        ticket.setHandlerName("超级管理员");
        ticket.setCreateTime(LocalDateTime.of(2026, 11, 5, 10, 0, 0));
        ticket.setAcceptTime(LocalDateTime.of(2026, 11, 5, 10, 5, 0));
        ticket.setFinishTime(LocalDateTime.of(2026, 11, 5, 10, 30, 0));
        ticket.setReply("已过等待期");

        byte[] data = TicketExcelExporter.write(List.of(ticket));
        assertThat(data).isNotEmpty();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = workbook.getSheet("服务工单");
            assertThat(sheet).isNotNull();
            Row header = sheet.getRow(0);
            assertThat((int) header.getLastCellNum()).isEqualTo(12);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("工单号");

            Row row = sheet.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("TK202611050001");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("张三");
            // 手机号中间四位必须被打码
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("139****0001");
            assertThat(row.getCell(5).getStringCellValue()).isEqualTo("紧急");
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("已完成");
        }
    }

    @Test
    @DisplayName("空列表也能导出一份只有表头的文件")
    void emptyListShouldStillWriteHeader() throws IOException {
        byte[] data = TicketExcelExporter.write(List.of());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = workbook.getSheet("服务工单");
            assertThat(sheet.getRow(0)).isNotNull();
            assertThat(sheet.getRow(1)).isNull();
        }
    }
}
