/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.codec;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.SwingWorker;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.dicom.codec.display.PresetWindowLevel;

import br.com.animati.texturedicom.ColorMask;
import br.com.animati.texturedicom.cl.CLConvolution;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 19 ago
 */
public final class StaticHelpers {

    private static Map<String, List<PresetWindowLevel>> presetsByModality;

    /** Class logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(StaticHelpers.class);

    public static ColorMask LUT_NONE = new ColorMask() {
        @Override
        public String toString() {
            return "Default";
        }
    };
    public static ColorMask LUT_VOLUMETRIC = new ColorMask() {
        @Override
        public String toString() {
            return "Volumetric";
        }
    };

    public static Map<String, List<PresetWindowLevel>> getPresetListByModality() {
        if (presetsByModality == null) {
            presetsByModality = getPresetListFromFile();
        }
        return presetsByModality;
    }

    private static Map<String, List<PresetWindowLevel>> getPresetListFromFile() {

        Map<String, List<PresetWindowLevel>> presetListByModality = new TreeMap<String, List<PresetWindowLevel>>();

        XMLStreamReader xmler = null;
        InputStream stream = null;
        try {
            XMLInputFactory xmlif = XMLInputFactory.newInstance();
            stream = new FileInputStream(ResourceUtil.getResource("presets.xml"));
            xmler = xmlif.createXMLStreamReader(stream);

            int eventType;
            while (xmler.hasNext()) {
                eventType = xmler.next();
                switch (eventType) {
                    case XMLStreamConstants.START_ELEMENT:
                        String key = xmler.getName().getLocalPart();
                        if ("presets".equals(key)) {
                            while (xmler.hasNext()) {
                                eventType = xmler.next();
                                switch (eventType) {
                                    case XMLStreamConstants.START_ELEMENT:
                                        key = xmler.getName().getLocalPart();
                                        if ("preset".equals(key) && xmler.getAttributeCount() >= 4) {
                                            String name = xmler.getAttributeValue(null, "name");
                                            try {
                                                String modality = xmler.getAttributeValue(null, "modality");
                                                float window =
                                                    Float.parseFloat(xmler.getAttributeValue(null, "window"));
                                                float level = Float.parseFloat(xmler.getAttributeValue(null, "level"));
                                                String shape = xmler.getAttributeValue(null, "shape");
                                                String keyCode = xmler.getAttributeValue(null, "key");
                                                LutShape lutShape = LutShape.getLutShape(shape);
                                                PresetWindowLevel preset = new PresetWindowLevel(name, window, level,
                                                    lutShape == null ? LutShape.LINEAR : lutShape);
                                                if (keyCode != null) {
                                                    preset.setKeyCode(Integer.parseInt(keyCode));
                                                }
                                                List<PresetWindowLevel> presetList = presetListByModality.get(modality);
                                                if (presetList == null) {
                                                    presetListByModality.put(modality,
                                                        presetList = new ArrayList<PresetWindowLevel>());
                                                }
                                                presetList.add(preset);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        catch (Exception e) {
            LOGGER.error("Cannot read presets file! " + e.getMessage());
        } finally {
            FileUtil.safeClose(xmler);
            FileUtil.safeClose(stream);
        }
        return presetListByModality;
    }

    public static List<ColorMask> buildColorMaskList() {
        final List<ColorMask> list = new ArrayList<ColorMask>();

        // Load in another thread - can take some time.
        // loadColorMaps(list);
        buildColorMask(list);

        return list;
    }

    private static void loadColorMaps(List<ColorMask> list) {
        new ColorMapLoader(list).execute();
    }

    public static List<TextureKernel> buildKernelList() {
        final List<TextureKernel> list = new ArrayList<TextureKernel>();
        list.add(new TextureKernel(org.weasis.core.api.Messages.getString("KernelData.0"),
            CLConvolution.ConvolutionPreset.Identity3));
        list.add(new TextureKernel(org.weasis.core.api.Messages.getString("KernelData.2"),
            CLConvolution.ConvolutionPreset.Blur3));
        list.add(new TextureKernel(Messages.getString("Kernel.Blur5"), CLConvolution.ConvolutionPreset.Blur5));
        list.add(new TextureKernel(Messages.getString("Kernel.Blur7"), CLConvolution.ConvolutionPreset.Blur7));
        list.add(
            new TextureKernel(Messages.getString("Kernel.Laplacian3"), CLConvolution.ConvolutionPreset.Laplacian3));
        list.add(
            new TextureKernel(Messages.getString("Kernel.Laplacian5"), CLConvolution.ConvolutionPreset.Laplacian5));
        list.add(
            new TextureKernel(Messages.getString("Kernel.EdgeDetect3"), CLConvolution.ConvolutionPreset.EdgeDetect3));
        list.add(new TextureKernel(org.weasis.core.api.Messages.getString("KernelData.11"),
            CLConvolution.ConvolutionPreset.Emboss3));
        return list;
    }

    public static class TextureKernel {
        private String kName;
        CLConvolution.ConvolutionPreset kernelPreset;

        public TextureKernel(String name, CLConvolution.ConvolutionPreset preset) {
            kName = name;
            kernelPreset = preset;
        }

        public CLConvolution.ConvolutionPreset getPreset() {
            return kernelPreset;
        }

        @Override
        public String toString() {
            return kName;
        }
    }

    private static List<ColorMask> buildColorMask(List<ColorMask> cmList) {
        File lutFolder = ResourceUtil.getResource("luts");
        if (lutFolder.exists() && lutFolder.isDirectory()) {
            File[] files = lutFolder.listFiles();
            for (File file : files) {
                if (file.canRead()) {
                    String name = file.getName();
                    final String lutName = name.substring(name.lastIndexOf("/") + 1, name.lastIndexOf("."));
                    ColorMask mask = new ColorMask() {
                        @Override
                        public String toString() {
                            return lutName;
                        }
                    };
                    cmList.add(mask);
                    mask.setImageFromTxt(file);
                    mask.mode = ColorMask.Mode.WindowedReplacement;
                }
            }
        }

        cmList.add(LUT_VOLUMETRIC);
        LUT_VOLUMETRIC.setImage(StaticHelpers.class.getResource("/textures/DefaultVolumetricColorMask.png"));

        Collections.sort(cmList, new Comparator<ColorMask>() {
            @Override
            public int compare(ColorMask o1, ColorMask o2) {
                if (o1 == null || o2 == null || o1.toString() == null || o2.toString() == null) {
                    return 0;
                }
                return o1.toString().compareTo(o2.toString());
            }
        });

        cmList.add(0, LUT_NONE);
        return cmList;
    }

    /**
     * Loads ColorMap data inBackground.
     */
    private static class ColorMapLoader extends SwingWorker<List<ColorMask>, Void> {
        private final List<ColorMask> cmList;

        protected ColorMapLoader(List<ColorMask> list) {
            cmList = list;
        }

        @Override
        protected List<ColorMask> doInBackground() throws Exception {
            File lutFolder = ResourceUtil.getResource("luts");
            if (lutFolder.exists() && lutFolder.isDirectory()) {
                File[] files = lutFolder.listFiles();
                for (File file : files) {
                    if (file.canRead()) {
                        String name = file.getName();
                        final String lutName = name.substring(name.lastIndexOf("/") + 1, name.lastIndexOf("."));
                        ColorMask mask = new ColorMask() {
                            @Override
                            public String toString() {
                                return lutName;
                            }
                        };
                        cmList.add(mask);
                        mask.setImageFromTxt(file);
                        mask.mode = ColorMask.Mode.WindowedReplacement;
                    }
                }
            }

            cmList.add(LUT_VOLUMETRIC);
            LUT_VOLUMETRIC.setImage(StaticHelpers.class.getResource("/textures/DefaultVolumetricColorMask.png"));

            Collections.sort(cmList, new Comparator<ColorMask>() {
                @Override
                public int compare(ColorMask o1, ColorMask o2) {
                    if (o1 == null || o2 == null || o1.toString() == null || o2.toString() == null) {
                        return 0;
                    }
                    return o1.toString().compareTo(o2.toString());
                }
            });

            cmList.add(0, LUT_NONE);
            return cmList;
        }

        @Override
        protected void done() {
            try {
                // Just to show errors when they hapen!
                List<ColorMask> get = get();
            } catch (Exception ignore) {
                ignore.printStackTrace();
            }
        }

    }
}