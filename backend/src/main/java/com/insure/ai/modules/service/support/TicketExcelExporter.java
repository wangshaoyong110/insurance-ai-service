package com.insure.ai.modules.service.support;

import com.insure.ai.common.BusinessException;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.TicketReasonEnum;
import com.insure.ai.common.enums.TicketStatusEnum;
import com.insure.ai.common.util.DesensitizeUtil;
import com.insure.ai.modules.service.entity.ServiceTicket;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 工单 Excel 导出器
 * <p>
 * 用 SXSSF(流式写)而不是 XSSF:导出量随工单增长,一次性把几万行放进内存容易 OOM,
 * SXSSF 只保留窗口内的行,写完自动清理临时文件。
 * 手机号出口脱敏,与页面展示口径一致——导出文件常被转发,不能成为敏感信息泄露渠道。
 * </p>
 *
 * @author shaoyong.wang
 */
public final class TicketExcelExporter {

    /** 单次导出上限:超过只导出前 N 条,避免误点导出把库拖垮 */
    public static final int MAX_ROWS = 50000;

    /** 表头 */
    private static final String[] HEADERS = {
            "工单号", "客户姓名", "手机号", "问题摘要", "转人工原因",
            "优先级", "状态", "处理人", "提交时间", "接单时间", "结单时间", "坐席回复"
    };

    /** 列宽(字符数) */
    private static final int[] COLUMN_WIDTHS = {22, 12, 16, 46, 16, 10, 10, 12, 20, 20, 20, 46};

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TicketExcelExporter() {
    }

    /**
     * 生成 xlsx 字节流
     */
    public static byte[] write(List<ServiceTicket> tickets) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("服务工单");
            CellStyle headerStyle = buildHeaderStyle(workbook);
            Row header = sheet.createRow(0);
            header.setHeightInPoints(22);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }
            int rowIndex = 1;
            for (ServiceTicket ticket : tickets) {
                if (rowIndex > MAX_ROWS) {
                    break;
                }
                Row row = sheet.createRow(rowIndex++);
                int col = 0;
                row.createCell(col++).setCellValue(text(ticket.getTicketNo()));
                row.createCell(col++).setCellValue(text(ticket.getCustomerName()));
                // 导出文件会被转发,手机号必须脱敏
                row.createCell(col++).setCellValue(DesensitizeUtil.phone(ticket.getCustomerPhone()));
                row.createCell(col++).setCellValue(text(ticket.getQuestion()));
                row.createCell(col++).setCellValue(TicketReasonEnum.descOf(ticket.getReason()));
                row.createCell(col++).setCellValue(
                        ticket.getPriority() != null && ticket.getPriority() == 2 ? "紧急" : "普通");
                row.createCell(col++).setCellValue(TicketStatusEnum.descOf(ticket.getStatus()));
                row.createCell(col++).setCellValue(text(ticket.getHandlerName()));
                row.createCell(col++).setCellValue(time(ticket.getCreateTime()));
                row.createCell(col++).setCellValue(time(ticket.getAcceptTime()));
                row.createCell(col++).setCellValue(time(ticket.getFinishTime()));
                row.createCell(col++).setCellValue(text(ticket.getReply()));
            }
            for (int i = 0; i < COLUMN_WIDTHS.length; i++) {
                sheet.setColumnWidth(i, COLUMN_WIDTHS[i] * 256);
            }
            // 冻结表头:滚动时列名始终可见
            sheet.createFreezePane(0, 1);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "导出失败,请稍后重试");
        }
    }

    /**
     * 表头样式:深蓝底白字加粗,带边框
     */
    private static CellStyle buildHeaderStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String time(LocalDateTime value) {
        return value == null ? "" : value.format(TIME_FORMATTER);
    }
}
