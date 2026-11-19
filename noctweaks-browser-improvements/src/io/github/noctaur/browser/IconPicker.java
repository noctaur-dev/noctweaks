package io.github.noctaur.browser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * "Set Icon…": a small window to pick a Lucide icon and one of Bitwig's colours for collections, folders or a
 * sidebar button. Search matches icon names and Lucide's tags ("drum", "keys", "voice"). Saving writes
 * icons.txt, the same file the NocTweaks installer's Icons… window wrote, so both stay compatible.
 */
public final class IconPicker {
   private IconPicker() {
   }

   /** Set by main(): where icons.txt lives and where the Lucide files are. */
   private static File stateDir;
   private static Path iconsDir;

   /**
    * The window runs as its own small program (Bitwig's Java can't open windows itself), started by
    * {@link IconWindow}: IconPicker <state dir> <icons dir> <title> <key>...
    */
   public static void main(String[] args) {
      System.setProperty("java.awt.headless", "false");
      stateDir = new File(args[0]);
      iconsDir = Path.of(args[1]);
      String title = args[2];
      List<String> keys = new ArrayList<>(java.util.Arrays.asList(args).subList(3, args.length));
      SwingUtilities.invokeLater(() -> {
         try {
            build(keys, title);
            open.addWindowListener(new java.awt.event.WindowAdapter() {
               @Override
               public void windowClosed(java.awt.event.WindowEvent e) {
                  System.exit(0);
               }
            });
         } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
         }
      });
   }

   private static void log(String m, Throwable t) {
      System.err.println("[icons] " + m + (t != null ? ": " + t : ""));
   }

   static final String[] COLOURS = {"default", "red", "orange", "yellow", "lime", "green", "mint", "turquoise", "blue",
      "purple", "white", "light_grey", "grey", "dark_grey", "black"};

   private static final Map<String, Color> SWATCH = Map.ofEntries(
      Map.entry("default", new Color(0xD0, 0xD0, 0xD0)),
      Map.entry("red", new Color(0xD9, 0x2E, 0x24)), Map.entry("orange", new Color(0xFF, 0x83, 0x2B)),
      Map.entry("yellow", new Color(0xE5, 0xC0, 0x1E)), Map.entry("lime", new Color(0xA3, 0xC6, 0x3B)),
      Map.entry("green", new Color(0x3F, 0xB3, 0x4F)), Map.entry("mint", new Color(0x3E, 0xC9, 0xA3)),
      Map.entry("turquoise", new Color(0x2F, 0xB5, 0xD6)), Map.entry("blue", new Color(0x3B, 0x6E, 0xE0)),
      Map.entry("purple", new Color(0x9A, 0x55, 0xD6)), Map.entry("white", new Color(0xE8, 0xE8, 0xE8)),
      Map.entry("black", new Color(0x1F, 0x1F, 0x1F)), Map.entry("light_grey", new Color(0xB0, 0xB0, 0xB0)),
      Map.entry("grey", new Color(0x80, 0x80, 0x80)), Map.entry("dark_grey", new Color(0x4A, 0x4A, 0x4A)));

   /** A swatch colour: one of Bitwig's names, or a custom #rrggbb. */
   private static final Map<JToggleButton, String> COLOURS_OF = new java.util.IdentityHashMap<>();

   static Color swatch(String c) {
      if (c != null && c.matches("#[0-9a-fA-F]{6}")) {
         return new Color(Integer.parseInt(c.substring(1), 16));
      }
      return SWATCH.getOrDefault(c, FG);
   }

   private static final Color BG = new Color(0x26, 0x26, 0x26);
   private static final Color FG = new Color(0xDD, 0xDD, 0xDD);
   private static final Color ACCENT = new Color(0xFF, 0x83, 0x2B);

   private static volatile JDialog open;

   // ------------------------------------------------------------------ icons.txt

   static File file() {
      return new File(stateDir, "icons.txt");
   }

   /** key -> {icon, colour or null}, in file order. */
   static Map<String, String[]> read() {
      Map<String, String[]> out = new LinkedHashMap<>();
      File f = file();
      if (!f.isFile()) {
         return out;
      }
      try {
         for (String raw : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            String l = raw.strip();
            if (l.isEmpty() || l.startsWith("#")) {
               continue;
            }
            int eq = l.lastIndexOf('=');
            if (eq <= 0) {
               continue;
            }
            String[] v = l.substring(eq + 1).strip().split("\\s+");
            if (v.length > 0 && !v[0].isEmpty()) {
               out.put(l.substring(0, eq).strip(), new String[]{v[0], v.length > 1 ? v[1] : null});
            }
         }
      } catch (Throwable t) {
         log("Could not read icons.txt", t);
      }
      return out;
   }

   /** Sets (icon != null) or clears (icon == null) these keys, keeping every other line. */
   static void write(List<String> keys, String icon, String colour) throws Exception {
      Map<String, String[]> all = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      all.putAll(read());
      for (String k : keys) {
         if (icon == null) {
            all.remove(k);
         } else {
            all.put(k, new String[]{icon, "default".equals(colour) ? null : colour});
         }
      }
      StringBuilder sb = new StringBuilder();
      sb.append("# NocTweaks icons. key = lucide-icon [colour]\n");
      sb.append("# Folders end with /, collections use their full name, @sidebar is the Collections button, @tracks the Tracks button.\n");
      for (Map.Entry<String, String[]> e : all.entrySet()) {
         sb.append(e.getKey()).append(" = ").append(e.getValue()[0]);
         if (e.getValue()[1] != null) {
            sb.append(' ').append(e.getValue()[1]);
         }
         sb.append('\n');
      }
      File f = file();
      f.getParentFile().mkdirs();
      File tmp = new File(f.getParentFile(), "icons.txt.tmp");
      Files.writeString(tmp.toPath(), sb.toString(), StandardCharsets.UTF_8);
      Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
   }

   // ------------------------------------------------------------------ Lucide

   private static volatile List<String> names;
   private static volatile Map<String, String> tags;   // icon -> its tags, space-separated

   private static Path dir() {
      return iconsDir;
   }

   private static List<String> names() {
      List<String> n = names;
      if (n == null) {
         n = new ArrayList<>();
         try {
            for (String l : Files.readAllLines(dir().resolve("lucide-names.txt"), StandardCharsets.UTF_8)) {
               if (!l.isBlank()) {
                  n.add(l.strip());
               }
            }
         } catch (Throwable t) {
            log("Could not read the icon list", t);
         }
         names = n;
      }
      return n;
   }

   /** Lucide's tags.json, read with a small pattern (one icon's tags per entry). */
   private static Map<String, String> tags() {
      Map<String, String> m = tags;
      if (m == null) {
         m = new HashMap<>();
         try {
            String json = Files.readString(dir().resolve("lucide-tags.json"), StandardCharsets.UTF_8);
            Matcher e = Pattern.compile("\"([a-z0-9-]+)\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(json);
            while (e.find()) {
               StringBuilder sb = new StringBuilder();
               Matcher w = Pattern.compile("\"([^\"]*)\"").matcher(e.group(2));
               while (w.find()) {
                  sb.append(w.group(1).toLowerCase(Locale.ROOT)).append(' ');
               }
               m.put(e.group(1), sb.toString());
            }
         } catch (Throwable t) {
            log("Could not read the icon tags", t);
         }
         tags = m;
      }
      return m;
   }

   private static final Map<String, ImageIcon> PREVIEWS = new HashMap<>();

   /** The icon's glyph, tinted, at 28 px. */
   private static ImageIcon preview(String icon, Color colour) {
      String key = icon + "|" + colour.getRGB();
      ImageIcon cached = PREVIEWS.get(key);
      if (cached != null) {
         return cached;
      }
      BufferedImage out = new BufferedImage(28, 28, BufferedImage.TYPE_INT_ARGB);
      try {
         BufferedImage glyph = ImageIO.read(dir().resolve("lucide-png/" + icon + ".png").toFile());
         if (glyph != null) {
            BufferedImage tinted = new BufferedImage(glyph.getWidth(), glyph.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D t = tinted.createGraphics();
            t.drawImage(glyph, 0, 0, null);
            t.setComposite(java.awt.AlphaComposite.SrcIn);
            t.setColor(colour);
            t.fillRect(0, 0, glyph.getWidth(), glyph.getHeight());
            t.dispose();
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(tinted.getScaledInstance(28, 28, Image.SCALE_SMOOTH), 0, 0, null);
            g.dispose();
         }
      } catch (Throwable ignored) {
      }
      ImageIcon i = new ImageIcon(out);
      if (PREVIEWS.size() > 6000) {
         PREVIEWS.clear();
      }
      PREVIEWS.put(key, i);
      return i;
   }

   // ------------------------------------------------------------------ the window

   private static void build(List<String> keys, String title) {
      try {
         UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
      } catch (Throwable ignored) {
      }
      JDialog old = open;
      if (old != null) {
         old.dispose();
      }
      JDialog d = new JDialog((java.awt.Frame) null, "Set Icon: " + title, false);
      d.setModalityType(java.awt.Dialog.ModalityType.MODELESS);
      open = d;
      d.setAlwaysOnTop(true);
      d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

      String[] current = read().get(keys.get(0));
      final String[] pickedIcon = {current != null ? current[0] : null};
      final String[] pickedColour = {current != null && current[1] != null ? current[1] : "default"};

      JPanel root = new JPanel(new BorderLayout(8, 8));
      root.setBackground(BG);
      root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

      // search
      JTextField search = new JTextField();
      search.setBackground(new Color(0x1A, 0x1A, 0x1A));
      search.setForeground(FG);
      search.setCaretColor(FG);
      search.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x44, 0x44, 0x44)),
         BorderFactory.createEmptyBorder(4, 6, 4, 6)));
      JLabel hint = label("Search icons (name or meaning, e.g. drum, piano, mic, star)");
      JPanel top = new JPanel(new BorderLayout(4, 4));
      top.setBackground(BG);
      top.add(hint, BorderLayout.NORTH);
      top.add(search, BorderLayout.CENTER);

      // colours
      JPanel colours = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
      colours.setBackground(BG);
      List<JToggleButton> swatches = new ArrayList<>();

      // grid
      DefaultListModel<String> model = new DefaultListModel<>();
      JList<String> grid = new JList<>(model);
      grid.setLayoutOrientation(JList.HORIZONTAL_WRAP);
      grid.setVisibleRowCount(-1);
      grid.setFixedCellWidth(44);
      grid.setFixedCellHeight(44);
      grid.setBackground(new Color(0x1A, 0x1A, 0x1A));
      grid.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      grid.setCellRenderer(new DefaultListCellRenderer() {
         @Override
         public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, "", index, selected, false);
            l.setIcon(preview((String) value, swatch(pickedColour[0])));
            l.setHorizontalAlignment(JLabel.CENTER);
            l.setToolTipText((String) value);
            l.setBackground(selected ? new Color(0x4A, 0x36, 0x24) : new Color(0x1A, 0x1A, 0x1A));
            l.setBorder(selected ? BorderFactory.createLineBorder(ACCENT, 2) : BorderFactory.createEmptyBorder(2, 2, 2, 2));
            return l;
         }
      });
      JScrollPane scroll = new JScrollPane(grid);
      scroll.setBorder(BorderFactory.createLineBorder(new Color(0x44, 0x44, 0x44)));
      scroll.getVerticalScrollBar().setUnitIncrement(22);

      JLabel status = label(keys.size() == 1 ? "For: " + keys.get(0) : "For " + keys.size() + " items");
      JLabel chosen = label(pickedIcon[0] == null ? "Bitwig's own icon" : pickedIcon[0]);
      chosen.setFont(chosen.getFont().deriveFont(Font.BOLD));

      Runnable fill = () -> {
         String q = search.getText().strip().toLowerCase(Locale.ROOT);
         model.clear();
         Map<String, String> tg = q.isEmpty() ? Map.of() : tags();
         List<String> nameHits = new ArrayList<>();
         List<String> tagHits = new ArrayList<>();
         for (String n : names()) {
            if (q.isEmpty() || n.contains(q.replace(' ', '-'))) {
               nameHits.add(n);
            } else if (tg.getOrDefault(n, "").contains(q)) {
               tagHits.add(n);
            }
         }
         for (String n : nameHits) {
            model.addElement(n);
         }
         for (String n : tagHits) {
            model.addElement(n);
         }
         if (pickedIcon[0] != null) {
            grid.setSelectedValue(pickedIcon[0], true);
         }
      };

      for (String c : COLOURS) {
         JToggleButton b = new JToggleButton() {
            @Override
            protected void paintComponent(Graphics g) {
               Graphics2D g2 = (Graphics2D) g;
               g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
               g2.setColor(BG);
               g2.fillRect(0, 0, getWidth(), getHeight());
               g2.setColor(swatch(c));
               g2.fillRoundRect(3, 3, getWidth() - 6, getHeight() - 6, 6, 6);
               if (isSelected()) {
                  g2.setColor(Color.WHITE);
                  g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 8, 8);
               }
            }
         };
         b.setPreferredSize(new Dimension(24, 24));
         b.setToolTipText("default".equals(c) ? "The collection's own colour" : c.replace('_', ' '));
         b.setSelected(c.equals(pickedColour[0]));
         b.setFocusable(false);
         b.addActionListener(e -> {
            pickedColour[0] = c;
            for (JToggleButton o : swatches) {
               o.setSelected(o == b);
            }
            grid.repaint();
         });
         COLOURS_OF.put(b, c);
         swatches.add(b);
         colours.add(b);
      }

      // Custom colour: a colour wheel (rainbow swatch until a colour is picked, then that colour).
      final String[] custom = {pickedColour[0].startsWith("#") ? pickedColour[0] : null};
      JToggleButton wheel = new JToggleButton() {
         @Override
         protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG);
            g2.fillRect(0, 0, getWidth(), getHeight());
            int w = getWidth() - 6, h = getHeight() - 6;
            if (custom[0] != null) {
               g2.setColor(swatch(custom[0]));
               g2.fillRoundRect(3, 3, w, h, 6, 6);
            } else {
               for (int a = 0; a < 360; a += 10) {
                  g2.setColor(Color.getHSBColor(a / 360f, 0.8f, 0.95f));
                  g2.fillArc(3, 3, w, h, a, 11);
               }
            }
            if (isSelected()) {
               g2.setColor(Color.WHITE);
               g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 8, 8);
            }
         }
      };
      wheel.setPreferredSize(new Dimension(24, 24));
      wheel.setToolTipText("Custom colour…");
      wheel.setSelected(custom[0] != null);
      wheel.setFocusable(false);
      wheel.addActionListener(e -> {
         Color start = custom[0] != null ? swatch(custom[0]) : ACCENT;
         Color c = javax.swing.JColorChooser.showDialog(d, "Custom colour", start);
         if (c != null) {
            custom[0] = String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
            pickedColour[0] = custom[0];
         }
         boolean on = custom[0] != null && custom[0].equals(pickedColour[0]);
         for (JToggleButton o : swatches) {
            o.setSelected(on ? o == wheel : COLOURS_OF.get(o) != null && COLOURS_OF.get(o).equals(pickedColour[0]));
         }
         wheel.setSelected(on);
         grid.repaint();
      });
      swatches.add(wheel);
      colours.add(wheel);

      grid.addListSelectionListener(e -> {
         if (!e.getValueIsAdjusting() && grid.getSelectedValue() != null) {
            pickedIcon[0] = grid.getSelectedValue();
            chosen.setText(pickedIcon[0]);
         }
      });

      JButton save = button("Save", true);
      JButton clear = button("Use Bitwig's Icon", false);
      JButton close = button("Close", false);
      Runnable doSave = () -> {
         try {
            if (pickedIcon[0] == null) {
               status.setText("Pick an icon first");
               return;
            }
            write(keys, pickedIcon[0], pickedColour[0]);
            status.setText("Saved. Bitwig picks it up in a moment (scroll or reopen the browser to redraw).");
         } catch (Throwable t) {
            status.setText("Couldn't save: " + t.getMessage());
            log("Could not save icons.txt", t);
         }
      };
      save.addActionListener(e -> doSave.run());
      grid.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
               doSave.run();
            }
         }
      });
      clear.addActionListener(e -> {
         try {
            write(keys, null, null);
            pickedIcon[0] = null;
            chosen.setText("Bitwig's own icon");
            grid.clearSelection();
            status.setText("Back to Bitwig's own icon.");
         } catch (Throwable t) {
            status.setText("Couldn't save: " + t.getMessage());
         }
      });
      close.addActionListener(e -> d.dispose());
      search.addKeyListener(new KeyAdapter() {
         @Override
         public void keyReleased(KeyEvent e) {
            fill.run();
         }
      });

      JPanel bottom = new JPanel(new BorderLayout(6, 6));
      bottom.setBackground(BG);
      JPanel colourRow = new JPanel(new BorderLayout(6, 0));
      colourRow.setBackground(BG);
      colourRow.add(label("Colour"), BorderLayout.WEST);
      colourRow.add(colours, BorderLayout.CENTER);
      JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
      buttons.setBackground(BG);
      buttons.add(clear);
      buttons.add(save);
      buttons.add(close);
      JPanel info = new JPanel(new BorderLayout());
      info.setBackground(BG);
      info.add(chosen, BorderLayout.NORTH);
      info.add(status, BorderLayout.SOUTH);
      bottom.add(colourRow, BorderLayout.NORTH);
      bottom.add(info, BorderLayout.CENTER);
      bottom.add(buttons, BorderLayout.SOUTH);

      root.add(top, BorderLayout.NORTH);
      root.add(scroll, BorderLayout.CENTER);
      root.add(bottom, BorderLayout.SOUTH);
      d.setContentPane(root);
      d.setSize(560, 600);
      d.setLocationRelativeTo(null);
      fill.run();
      d.setVisible(true);
      search.requestFocusInWindow();
   }

   private static JLabel label(String s) {
      JLabel l = new JLabel(s);
      l.setForeground(FG);
      return l;
   }

   private static JButton button(String s, boolean primary) {
      JButton b = new JButton(s);
      b.setFocusable(false);
      b.setBackground(primary ? ACCENT : new Color(0x3A, 0x3A, 0x3A));
      b.setForeground(primary ? Color.BLACK : FG);
      b.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
      return b;
   }
}
