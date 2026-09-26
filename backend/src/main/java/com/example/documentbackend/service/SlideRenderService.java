package com.example.documentbackend.service;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
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

    // 한글이 네모박스(□□□)로 깨져서 렌더링되는 문제의 대책. 원인은 이 자바 프로세스가 돌아가는
    // 호스트(특히 일부 Windows 환경의 JDK)에 한글 글리프를 그릴 수 있는 폰트가 전혀 없어서 -
    // AWT가 폰트를 못 찾으면 대체(fallback)를 시도하다 그마저도 실패하면 빈 사각형을 그린다.
    // 호스트에 어떤 폰트가 깔려 있는지에 기대지 않도록, 한글 글리프가 보장된 폰트(Noto Sans KR,
    // SIL OFL 라이선스, resources/fonts에 내장)를 애플리케이션 시작 시 직접 등록해두고,
    // 실제 텍스트를 표시할 수 없는 run만 이 폰트로 강제 전환한다(sanitizeFonts 참고).
    private static final String FALLBACK_FONT_FAMILY = loadFallbackFont();

    private static String loadFallbackFont() {
        try (InputStream in = SlideRenderService.class.getResourceAsStream("/fonts/NotoSansKR-Regular.ttf")) {
            if (in == null) {
                System.err.println("[SlideRenderService] 한글 대체 폰트 리소스를 찾을 수 없습니다"
                        + "(/fonts/NotoSansKR-Regular.ttf) - 호스트 기본 폰트로 대체됩니다.");
                return null;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            System.out.println("[SlideRenderService] 한글 대체 폰트 등록 완료: family=" + font.getFamily()
                    + ", canDisplay('가')=" + font.canDisplay('가'));
            return font.getFamily();
        } catch (IOException | FontFormatException | RuntimeException e) {
            // 폰트 로딩 자체가 실패해도 변환 기능은 계속 동작해야 한다 - 이 경우 호스트의
            // 기본 폰트 대체에 맡긴다(등록 전과 동일한 동작). 파일이 깨져있으면(예: Windows
            // Git의 줄바꿈 자동 변환으로 바이너리 폰트 파일이 손상된 경우) 보통 여기서
            // FontFormatException이 난다.
            System.err.println("[SlideRenderService] 한글 대체 폰트 로딩 실패 - 호스트 기본 폰트로 대체됩니다: " + e);
            return null;
        }
    }

    public List<byte[]> renderToPng(InputStream input, String fileType) throws IOException {
        return "pptx".equals(fileType) ? renderXslf(input) : renderHslf(input);
    }

    private List<byte[]> renderXslf(InputStream input) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(input)) {
            Dimension pageSize = ppt.getPageSize();
            List<byte[]> result = new ArrayList<>();
            for (XSLFSlide slide : ppt.getSlides()) {
                sanitizeLineSpacing(slide);
                sanitizeFonts(slide);
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
                sanitizeFonts(slide);
                result.add(renderSlide(slide::draw, pageSize));
            }
            return result;
        }
    }

    // run이 실제로 가진 텍스트를 그 run에 지정된 폰트(를 AWT가 실제로 찾아낸 폰트)가 단 한
    // 글자라도 표시할 수 없으면, 내장해둔 한글 폰트로 강제 전환해서 네모박스 대신 실제
    // 글자가 나오게 한다. 원래 폰트로 정상 표시되는 run(대부분의 영문/이미 잘 지원되는
    // 한글 폰트)은 건드리지 않는다.
    private static <S extends Shape<S, P>, P extends TextParagraph<S, P, ? extends TextRun>> void sanitizeFonts(
            Sheet<S, P> sheet) {
        if (FALLBACK_FONT_FAMILY == null) return;
        for (S shape : sheet.getShapes()) {
            if (!(shape instanceof TextShape)) continue;
            @SuppressWarnings("unchecked")
            TextShape<S, P> textShape = (TextShape<S, P>) shape;
            for (P paragraph : textShape.getTextParagraphs()) {
                for (TextRun run : paragraph.getTextRuns()) {
                    String text = run.getRawText();
                    if (text == null || text.isEmpty()) continue;
                    String family = run.getFontFamily();
                    int style = Font.PLAIN;
                    Font font = new Font(family, style, 12);
                    if (font.canDisplayUpTo(text) != -1) {
                        run.setFontFamily(FALLBACK_FONT_FAMILY);
                    }
                }
            }
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
