/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Inform??tica Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import javax.vecmath.Vector3d;

import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.editor.image.PixelInfo;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.utils.imp.DefaultGraphicLabel;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.CornerDisplay;
import org.weasis.dicom.codec.display.CornerInfoData;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.display.ModalityInfoData;
import org.weasis.dicom.codec.display.ModalityView;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.geometry.ImageOrientation.Label;
import org.weasis.dicom.explorer.DicomModel;

import br.com.animati.texture.codec.TextureDicomSeries;
import br.com.animati.texture.mpr3dview.api.AbstractInfoLayer;
import br.com.animati.texture.mpr3dview.api.ActionWA;
import br.com.animati.texture.mpr3dview.api.DisplayUtils;
import br.com.animati.texture.mpr3dview.internal.Messages;
import br.com.animati.texturedicom.ImageSeries;

/**
 *
 * @author Gabriela Carla Bauerman (gabriela@animati.com.br)
 */
public class InfoLayer3d extends AbstractInfoLayer {

    protected ViewTexture owner;

    private PixelInfo pixelInfo = null;
    private final Rectangle pixelInfoBound;

    public InfoLayer3d(ViewTexture view) {
        owner = view;
        initDisplayPrefs();
        pixelInfoBound = new Rectangle();
    }

    private void initDisplayPrefs() {
        displayPreferences.put(ANNOTATIONS, true);
        displayPreferences.put(ANONYM_ANNOTATIONS, false);
        displayPreferences.put(IMAGE_ORIENTATION, true);
        displayPreferences.put(ORIENTATION_TOP, true);
        displayPreferences.put(ORIENTATION_BOTTOM, false);
        displayPreferences.put(ORIENTATION_LEFT, true);
        displayPreferences.put(ORIENTATION_RIGHT, false);
        displayPreferences.put(SCALE, true);
        displayPreferences.put(SCALE_TOP, false);
        displayPreferences.put(SCALE_BOTTOM, true);
        displayPreferences.put(SCALE_LEFT, true);
        displayPreferences.put(SCALE_RIGHT, false);
        displayPreferences.put(PIXEL, true);
        displayPreferences.put(WINDOW_LEVEL, true);
        displayPreferences.put(ZOOM, true);
        displayPreferences.put(ROTATION, false);
        displayPreferences.put(FRAME, true);
        displayPreferences.put(PRELOADING_BAR, true);
        displayPreferences.put(MODALITY, true);
        displayPreferences.put(MIN_ANNOTATIONS, false);
    }

    @Override
    protected void paintNotReadable(Graphics2D g2d) {
        String message;
        Rectangle bounds = getOwnerBounds();
        float midx = bounds.width / 2f;
        float midy = bounds.height / 2f;

        float y = midy;
        message = Messages.getString("InfoLayer3d.msg_not_read");
        paintFontOutline(g2d, message, midx - g2d.getFontMetrics().stringWidth(message) / (float) 2, y, Color.RED,
            Color.BLACK);
    }

    @Override
    protected boolean ownerHasContent() {
        return owner.hasContent();
    }

    @Override
    protected boolean isOwnerContentReadable() {
        return owner.isContentReadable();
    }

    @Override
    protected Rectangle getOwnerBounds() {
        return owner.getJComponent().getBounds();
    }

    @Override
    public void paintTextCorners(Graphics2D g2d) {
        Color oldColor = g2d.getColor();

        final Rectangle bound = getOwnerBounds();
        final float fontHeight = FontTools.getAccurateFontHeight(g2d);
        float drawY = bound.height - border - 1.5f; // -1.5 for outline
        boolean hideMin = !getDisplayPreferences(MIN_ANNOTATIONS);

        /* Informações no canto inferior esquerdo */
        if (owner instanceof ViewTexture) {
            drawY = drawIrregularSpacingAlert(g2d, owner, drawY, fontHeight);
        }

        /* Lossy compression */
        drawY = checkAndPaintLossy(g2d, owner, drawY, fontHeight);

        /* MODALITY - ORIENTATION */
        if (getDisplayPreferences(MODALITY)) {
            if (owner instanceof ViewTexture) {
                ImageSeries imSeries = owner.getParentImageSeries();
                StringBuffer orientation = new StringBuffer();
                if (imSeries instanceof TextureDicomSeries) {
                    TextureDicomSeries textureSeries = (TextureDicomSeries) imSeries;
                    Modality mod = Modality.getModality(TagD.getTagValue(textureSeries, Tag.Modality, String.class));
                    // ModalityInfoData modality = ModalityPrefView.getModlatityInfos(mod);

                    orientation.append(mod.name());
                    orientation.append(" (").append(textureSeries.getSliceWidth());
                    orientation.append("x").append(textureSeries.getSliceHeight());
                    orientation.append("x").append(textureSeries.getSliceCount());
                    orientation.append(")");

                    if (getDisplayPreferences(IMAGE_ORIENTATION)) {
                        String imgOrientation = getOwnerContentOrientationLabel();
                        if (imgOrientation != null) {
                            orientation.append(" - ");
                            orientation.append(imgOrientation);
                        }
                    }
                }

                DefaultGraphicLabel.paintFontOutline(g2d, orientation.toString(), border, drawY);
                drawY -= fontHeight;
            }
        }

        /* PIXEL */
        if (!isVolumetricView() && getDisplayPreferences(PIXEL) && hideMin) {
            StringBuilder sb = new StringBuilder(Messages.getString("InfoLayer3d.pixel"));
            sb.append(": ");

            if (pixelInfo != null) {
                if (pixelInfo.getPixelValueText() != null) {
                    sb.append(pixelInfo.getPixelValueText());
                    sb.append(" - ");
                }
                sb.append(pixelInfo.getPixelPositionText());
            }
            String strPixel = sb.toString();
            DefaultGraphicLabel.paintFontOutline(g2d, strPixel, border, drawY - 1);
            drawY -= fontHeight + 2;
            pixelInfoBound.setBounds(border - 2, (int) drawY + 3,
                g2d.getFontMetrics(owner.getLayerFont()).stringWidth(strPixel) + 4, (int) fontHeight + 2);
        }

        /* WINDOW_LEVEL */
        if (getDisplayPreferences(WINDOW_LEVEL) && hideMin) {
            DefaultGraphicLabel.paintFontOutline(g2d,
                Messages.getString("InfoLayer3d.WL") + " " + owner.windowingWindow + " / " + owner.windowingLevel,
                border, drawY);
            drawY -= fontHeight;
        }

        /* ZOOM */
        if (getDisplayPreferences(ZOOM) && hideMin) {
            double dispZoom = getOwnerZoomFactor();
            if (dispZoom > 0) {
                DefaultGraphicLabel.paintFontOutline(g2d, Messages.getString("InfoLayer3d.zoom") + " "
                    + DecFormater.twoDecimal(dispZoom * 100) + " " + Messages.getString("InfoLayer3d.percent_symb"),
                    border, drawY);
                drawY -= fontHeight;
            }
        }

        if (getDisplayPreferences(ROTATION) && hideMin) {
            Integer rotation = (Integer) owner.getActionValue(ActionW.ROTATION.cmd());
            if (rotation != null) {
                DefaultGraphicLabel.paintFontOutline(g2d, Messages.getString("InfoLayer.angle") + " "
                    + DecFormater.oneDecimal(rotation) + " " + Messages.getString("InfoLayer.angle_symb"), border,
                    drawY);
                drawY -= fontHeight;
            }
        }
        if (getDisplayPreferences(FRAME) && !isVolumetricView() && hideMin) {
            String instString = " [ ] ";
            Integer instance = getOwnerContentInstanceNumber();
            if (instance != null) {
                instString = " [" + instance + "] ";
            }
            Integer index = getOwnerContentFrameIndex();
            Integer size = getOwnerSeriesSize();
            if (index != null && size != null) {
                DefaultGraphicLabel.paintFontOutline(g2d,
                    Messages.getString("InfoLayer.frame") + instString + (index + 1) + " / " + size, border, drawY);
                drawY -= fontHeight;
            }
        }

        /* Demais informações */
        if (getDisplayPreferences(ANNOTATIONS)) {
            final Series series = (Series) owner.getSeries();

            DicomModel model = GUIManager.getInstance().getActiveDicomModel();
            MediaSeriesGroup study = model.getParent(series, DicomModel.study);
            MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);
            Modality mod = Modality.getModality(TagD.getTagValue(series, Tag.Modality, String.class));

            ModalityInfoData modality = ModalityView.getModlatityInfos(mod);
            CornerInfoData corner = modality.getCornerInfo(CornerDisplay.TOP_LEFT);

            boolean anonymize = getDisplayPreferences(ANONYM_ANNOTATIONS);
            drawY = fontHeight;
            TagView[] infos = corner.getInfos();
            for (int j = 0; j < infos.length; j++) {
                if (infos[j] != null) {
                    if (hideMin || infos[j].containsTag(TagD.get(Tag.PatientName))) {
                        for (TagW tag : infos[j].getTag()) {
                            if (!anonymize || tag.getAnonymizationType() != 1) {
                                Object value = DisplayUtils.getTagValue(tag, patient, study, series, null);
                                if (value != null) {
                                    String str = tag.getFormattedTagValue(value, infos[j].getFormat());
                                    if (StringUtil.hasText(str)) {
                                        DefaultGraphicLabel.paintFontOutline(g2d, str, border, drawY);
                                        drawY += fontHeight;
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            corner = modality.getCornerInfo(CornerDisplay.TOP_RIGHT);
            drawY = fontHeight;
            infos = corner.getInfos();
            for (int j = 0; j < infos.length; j++) {
                if (infos[j] != null) {
                    if (hideMin || infos[j].containsTag(TagD.get(Tag.SeriesDate))) {
                        Object value;
                        for (TagW tag : infos[j].getTag()) {
                            if (!anonymize || tag.getAnonymizationType() != 1) {
                                value = DisplayUtils.getTagValue(tag, patient, study, series, null);
                                if (tag.getKeyword().equals("AcquisitionDate")
                                    || tag.getKeyword().equals("AcquisitionTime")) {
                                    if (owner instanceof ViewTexture && owner.isShowingAcquisitionAxis()) {
                                        ViewTexture texture = owner;
                                        value = owner.getSeriesObject().getTagValue(tag, texture.getCurrentSlice() - 1);
                                    } else if (tag.getKeyword().equals("AcquisitionTime")) {
                                        break;
                                    }
                                }

                                if (value != null) {
                                    String str = tag.getFormattedTagValue(value, infos[j].getFormat());
                                    if (StringUtil.hasText(str)) {
                                        DefaultGraphicLabel.paintFontOutline(g2d, str,
                                            bound.width - g2d.getFontMetrics().stringWidth(str) - (float) border,
                                            drawY);
                                        drawY += fontHeight;
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (hideMin) {
                corner = modality.getCornerInfo(CornerDisplay.BOTTOM_RIGHT);
                drawY = bound.height - border - 1.5f; // -1.5 for outline
                infos = corner.getInfos();
                for (int j = infos.length - 1; j >= 0; j--) {
                    if (infos[j] != null) {
                        Object value = null;
                        for (TagW tag : infos[j].getTag()) {
                            if (!anonymize || tag.getAnonymizationType() != 1) {
                                value = DisplayUtils.getTagValue(tag, patient, study, series, null);

                                if (tag.getKeyword().equals("SliceLocation")
                                    || tag.getKeyword().equals("SliceThickness")) {
                                    if (owner instanceof ViewTexture && owner.isShowingAcquisitionAxis()
                                        && !isVolumetricView()) {
                                        ViewTexture texture = owner;
                                        value = owner.getSeriesObject().getTagValue(tag, texture.getCurrentSlice() - 1);

                                    }
                                }

                                if (value != null) {
                                    String str = tag.getFormattedTagValue(value, infos[j].getFormat());
                                    if (StringUtil.hasText(str)) {
                                        DefaultGraphicLabel.paintFontOutline(g2d, str,
                                            bound.width - g2d.getFontMetrics().stringWidth(str) - border, drawY);
                                        drawY -= fontHeight;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                if (getDisplayPreferences(PRELOADING_BAR)) {
                    paintPreLoadingBar((int) drawY, g2d, bound.width, bound.height);
                }
            }
        }
        g2d.setColor(oldColor);
    }

    @Override
    public String[] getContent4OrientationFlags() {
        return owner.getOrientationStrings();
    }

    @Override
    public Unit getOwnerPixelSpacingUnit() {
        if (owner instanceof ViewTexture) {
            ImageSeries ser = owner.getParentImageSeries();
            if (ser instanceof TextureDicomSeries) {
                return ((TextureDicomSeries) ser).getPixelSpacingUnit();
            }
        }
        return null;
    }

    @Override
    public double getOwnerZoomFactor() {
        Object zoom = owner.getActionValue(ActionW.ZOOM.cmd());
        if (zoom instanceof Double) {
            return Math.abs((Double) zoom);
        }
        return 0;
    }

    @Override
    public double getOwnerPixelSize() {
        if (owner instanceof ViewTexture) {
            return owner.getShowingPixelSize();
        }
        return 0;
    }

    @Override
    public Dimension getOwnerContentDimensions() {
        if (owner instanceof ViewTexture) {
            Vector3d imageSize = owner.getParentImageSeries().getImageSize();
            return new Dimension((int) imageSize.x, (int) imageSize.y);
        }
        return null;
    }

    @Override
    public double getOwnerContentRescaleX() {
        return 1.0;
    }

    @Override
    public double getOwnerContentRescaleY() {
        return 1.0;
    }

    @Override
    public String getPixelSizeCalibrationDescription() {
        String tagValue = null;
        if (owner instanceof ViewTexture) {
            TextureDicomSeries ser = (TextureDicomSeries) owner.getParentImageSeries();
            if (ser != null) {
                tagValue = TagD.getTagValue(ser, Tag.PixelSpacingCalibrationDescription, String.class);
            }
        }
        return tagValue;
    }

    @Override
    public Rectangle getPreloadingProgressBound() {
        return null;
    }

    @Override
    public Rectangle getPixelInfoBound() {
        return pixelInfoBound;
    }

    @Override
    public void setPixelInfo(PixelInfo pixelInfo) {
        this.pixelInfo = pixelInfo;
    }

    @Override
    public PixelInfo getPixelInfo() {
        return pixelInfo;
    }

    @Override
    public LayerAnnotation getLayerCopy(ViewCanvas view2DPane) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private float drawIrregularSpacingAlert(Graphics2D g2d, ViewTexture owner, float drawY, float fontHeight) {
        ImageSeries imSeries = owner.getParentImageSeries();
        if (imSeries instanceof TextureDicomSeries) {
            TextureDicomSeries textureSeries = (TextureDicomSeries) imSeries;

            /* AVISO SE O ESPAÇAMENTO DE SLICES FOR IRREGULAR */
            if (!textureSeries.isSliceSpacingRegular()) {
                if (isVolumetricView() || !owner.isShowingAcquisitionAxis()) {
                    String message = Messages.getString("InfoLayer3d.SliceSpacingWarning");
                    DefaultGraphicLabel.paintColorFontOutline(g2d, message, border, drawY, Color.red);
                    drawY -= fontHeight;
                }
            }
        }
        return drawY;
    }

    private float checkAndPaintLossy(Graphics2D g2d, ViewTexture owner, float drawY, float fontHeight) {
        String tsuid = null;
        if (owner instanceof ViewTexture) {
            String tagVal = TagD.getTagValue(owner.getSeriesObject(), Tag.TransferSyntaxUID, String.class);
            tsuid = DisplayUtils.getLossyTransferSyntaxUID(tagVal);
        }

        if (tsuid != null) {
            DefaultGraphicLabel.paintColorFontOutline(g2d, Messages.getString("InfoLayer.lossy") + " " + tsuid, border, drawY,
                Color.RED);
            drawY -= fontHeight;
        }
        return drawY;
    }

    private String getOwnerContentOrientationLabel() {
        double[] v = owner.getImagePatientOrientation();
        if (v != null && v.length == 6) {
            Label imgOrientation = ImageOrientation.makeImageOrientationLabelFromImageOrientationPatient(v[0], v[1],
                v[2], v[3], v[4], v[5]);
            return imgOrientation.toString();
        }
        return null;
    }

    private boolean isVolumetricView() {
        if (owner instanceof ViewTexture) {
            return (Boolean) owner.getActionValue(ActionWA.VOLUM_RENDERING.cmd());
        }
        return false;
    }

    private Integer getOwnerContentInstanceNumber() {
        if (owner instanceof ViewTexture) {
            ViewTexture vt = owner;
            if (isOwnerContentReadable() && !isVolumetricView() && vt.isShowingAcquisitionAxis()) {
                int currentSlice = vt.getCurrentSlice() - 1;
                Integer tag =
                    TagD.getTagValue((TextureDicomSeries) vt.getParentImageSeries(), Tag.InstanceNumber, Integer.class);
                if (tag != null) {
                    return tag == null ? currentSlice : tag;
                }
            }
        }
        return null;
    }

    private Integer getOwnerContentFrameIndex() {
        if (ownerHasContent() && isOwnerContentReadable()) {
            if (owner instanceof ViewTexture && owner.isShowingAcquisitionAxis()) {
                return owner.getCurrentSlice() - 1; // -1 para ficar iqual a numeracao do ImageViewer
            }
        }
        return null;
    }

    private Integer getOwnerSeriesSize() {
        if (owner instanceof ViewTexture) {
            return owner.getSeriesObject().getSliceCount();
        }
        return owner.getSeries().size(null);
    }

    private void paintPreLoadingBar(final int drawY, final Graphics2D g2d, final int width, final int height) {
        boolean[] imagesLoadies = null;
        if (owner instanceof ViewTexture) {
            if (owner.getSeriesObject() == null) {
                return;
            }
            imagesLoadies = owner.getSeriesObject().getPlacesInVideo();
        } else {
            if (owner.getSeries() == null || !(owner.getSeries() instanceof DicomSeries)) {
                return;
            }
            imagesLoadies = ((DicomSeries) owner.getSeries()).getImageInMemoryList();
        }
        Color oldColor = g2d.getColor();
        g2d.setPaint(Color.WHITE);

        final double lengthBar = 100;
        final double locationX = width - (lengthBar + 12);
        final int locationY = drawY - 3;
        final double size = imagesLoadies.length;
        final int scale = (int) Math.round(lengthBar / size);
        for (int i = 0; i < size; i++) {
            if (imagesLoadies[i]) {
                double printX = (lengthBar * (i + 1)) / size;
                if (scale <= 1) {
                    g2d.drawLine((int) (locationX + printX), locationY, (int) (locationX + printX), locationY + 3);
                } else {
                    // fills empty spaces
                    for (int j = (int) (printX - scale); j <= printX; j++) {
                        g2d.drawLine((int) locationX + j, locationY, (int) locationX + j, locationY + 3);
                    }
                }
            }
        }
        g2d.setColor(oldColor);
    }

}