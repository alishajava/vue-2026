package com.example.documentbackend.service;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.sl.usermodel.Shape;
import org.apache.poi.sl.usermodel.Sheet;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.TextRun;
import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.springframework.stereotype.Service;

/**
 * 구버전 .ppt(HSLF)와 .pptx(XSLF)를 슬라이드별 PNG로 변환한다.
 * 둘 다 Apache POI가 Graphics2D로 직접 그리는 기능(draw)을 제공해서, LibreOffice 같은
 * 외부 프로세스/VM 없이 순수 자바 코드만으로 렌더링할 수 있다 - 대신 LibreOffice보다
 * 폰트/그림자/SmartArt 같은 복잡한 요소의 정확도는 떨어질 수 있다.
 */
@Service
public class SlideRenderService {

    // 2배 해상도로 렌더링 - "크게보기"에서 확대해도 흐려지지 않게
    private static final int RENDER_SCALE = 2;

    public List<byte[]> renderToPng(InputStream input, String fileType) throws IOException {
        return "pptx".equals(fileType) ? renderXslf(input) : renderHslf(input);
    }

    private List<byte[]> renderXslf(InputStream input) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(input)) {
            Dimension pageSize = ppt.getPageSize();
            List<byte[]> result = new ArrayList<>();
            for (XSLFSlide slide : ppt.getSlides()) {
                sanitizeLineSpacing(slide);
                result.add(renderSlide(slide::draw, pageSize));
            }
            return result;
        }
    }

    private List<byte[]> renderHslf(InputStream input) throws IOException {
        try (HSLFSlideShow ppt = new HSLFSlideShow(input)) {
            Dimension pageSize = ppt.getPageSize();
            List<byte[]> result = new ArrayList<>();
            for (HSLFSlide slide : ppt.getSlides()) {
                sanitizeLineSpacing(slide);
                result.add(renderSlide(slide::draw, pageSize));
            }
            return result;
        }
    }

    // 문단의 줄간격이 너무 좁으면(폰트 크기보다 좁은 고정 포인트 값이거나, 100% 미만인
    // 비율 값) 그 문단이 여러 줄로 줄바꿈되거나 다음 문단과 인접할 때 POI가 줄들을 서로
    // 겹쳐서 그린다(실제 재현 확인 - 원본 파워포인트에서는 정상으로 보이던 슬라이드가 이
    // 변환에서만 글자가 겹쳐 보였다. 고정 포인트/비율 두 경우 모두에서 재현됨).
    // getLineSpacing()은 양수면 %(비율), 음수면 고정 포인트를 의미한다 - 두 경우 모두
    // 표준 줄 높이보다 좁으면 100%로 덮어써서 겹침을 막는다. 100% 이상으로 의도적으로
    // 넓게 잡은 간격은 그대로 둔다.
    private static <S extends Shape<S, P>, P extends TextParagraph<S, P, ? extends TextRun>> void sanitizeLineSpacing(
            Sheet<S, P> sheet) {
        for (S shape : sheet.getShapes()) {
            if (!(shape instanceof TextShape)) continue;
            @SuppressWarnings("unchecked")
            TextShape<S, P> textShape = (TextShape<S, P>) shape;
            for (P paragraph : textShape.getTextParagraphs()) {
                Double lineSpacing = paragraph.getLineSpacing();
                if (lineSpacing == null) continue;
                if (lineSpacing >= 0) {
                    // 비율(%) 값 - 100% 미만이면 줄들이 서로 겹칠 수 있다.
                    if (lineSpacing < 100.0) {
                        paragraph.setLineSpacing(100.0);
                    }
                    continue;
                }
                // 고정 포인트 값 - 그 문단에서 쓰인 폰트 크기보다 좁으면 겹칠 수 있다.
                double maxFontSize = 12.0;
                for (TextRun run : paragraph.getTextRuns()) {
                    if (run.getFontSize() != null) {
                        maxFontSize = Math.max(maxFontSize, run.getFontSize());
                    }
                }
                if (Math.abs(lineSpacing) < maxFontSize) {
                    paragraph.setLineSpacing(100.0);
                }
            }
        }
    }

    private byte[] renderSlide(Consumer<Graphics2D> drawFn, Dimension pageSize) throws IOException {
        BufferedImage img = new BufferedImage(
                pageSize.width * RENDER_SCALE, pageSize.height * RENDER_SCALE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.scale(RENDER_SCALE, RENDER_SCALE);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, pageSize.width, pageSize.height);
        drawFn.accept(g);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
