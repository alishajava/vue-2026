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
                result.add(renderSlide(slide::draw, pageSize));
            }
            return result;
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
