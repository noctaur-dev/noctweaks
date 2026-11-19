package io.github.noctaur.browser;

import bitwig.obf.*;
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;
import com.bitwig.flt.control_surface.proxy.CursorBrowserFilterItemProxy; // the browser as controller scripts see it
import com.bitwig.flt.document.core.iface.bMt;                           // a project document
import com.bitwig.flt.document.core.master.Awj;                          // what a dropped browser item is added to
import com.bitwig.flt.document.core.master.sJU;
import com.bitwig.flt.document.core.master.CmH;                          // a loaded project (a dropped preset)
import com.bitwig.base.io.Lpf;                                          // a library file (path + sub-item)                          // what a dropped browser item becomes

import java.net.URI;
import java.util.List;

/**
 * Every hook of Browser: Collections, Icons and Track Presets. The logic is in the Noc* classes.
 *
 * Several of the old patches replaced a method's answer outright. A hook can't skip a method, so those are
 * done where the answer is used instead: at each call (REPLACE_CALL), or by remembering the object on entry
 * and changing the returned value (ENTRY + WRAP_RETURN).
 */
public final class BrowserHooks {
   private BrowserHooks() {
   }

   private static final ThreadLocal<Object> NODE = new ThreadLocal<>();     // entry -> return, one method at a time
   private static final ThreadLocal<Object[]> ARGS = new ThreadLocal<>();

   private static Object take() {
      Object o = NODE.get();
      NODE.remove();
      return o;
   }

   // ================================================================== sidebar buttons (sources)

   /** Wd: the list of browser sources (sidebar buttons); its constructor adds Bitwig's. Add ours after them. */
   @Hook(target = Wd.class, method = "<init>", at = At.BEFORE_RETURN, id = "sources")
   public static void sources(@This Wd list) {
      try {
         list.beI(NocBrowser.source()); // add a source
         list.beI(NocTracks.source());
      } catch (Throwable t) {
         NocBrowser.log("could not add the Collections and Tracks buttons", t);
      }
   }

   /** g3b.beI(qrL): a sidebar button's label. */
   @Hook(target = g3b.class, method = "beI(LqrL;)Ljava/lang/String;", at = At.ENTRY, id = "label-entry")
   public static void labelEntry(@This g3b button) {
      NODE.set(button);
   }

   @Hook(target = g3b.class, method = "beI(LqrL;)Ljava/lang/String;", at = At.WRAP_RETURN, id = "label")
   public static String label(@Return String bitwig) {
      try {
         Object b = take();
         String mine = b instanceof g3b g ? NocBrowser.label(g.sCA) : null; // sCA: the button's source
         return mine != null ? mine : bitwig;
      } catch (Throwable t) {
         return bitwig;
      }
   }

   /** cCE.Bqk(): the sidebar's pinned buttons. Pin Collections and Tracks once. */
   private static final ThreadLocal<Object> PIN_OWNER = new ThreadLocal<>();

   @Hook(target = cCE.class, method = "Bqk()LZI;", at = At.ENTRY, id = "pin-entry")
   public static void pinEntry(@This cCE browser) {
      PIN_OWNER.set(browser);
   }

   /** Tracks goes on the main browser's sidebar only (cCH); popups (cCL, cFT) keep their own buttons. */
   @Hook(target = cCE.class, method = "Bqk()LZI;", at = At.WRAP_RETURN, id = "pin")
   public static Object pin(@Return ZI pinned) {
      Object owner = PIN_OWNER.get();
      PIN_OWNER.remove();
      NocBrowser.ensurePinned(pinned, owner instanceof cCH);
      return pinned;
   }

   /** Jy.beI(WV, boolean): a browser session switches to another sidebar button. */
   @Hook(target = Jy.class, method = "beI(LWV;Z)V", at = At.BEFORE_RETURN, id = "source-chosen")
   public static void sourceChosen(@This Jy session) {
      NocBrowser.sourceChosen(session);
   }

   /**
    * ZI: the quick sources. Dragging a button onto a full row (12) replaces one; with the count read as 0 here,
    * a drag adds it instead. (Pin to Quick Sources in a collection's menu has no limit either.)
    */
   @Hook(target = ZI.class, method = "beI(Lcom/bitwig/flt/widget/library/dnd/YEs;Lcom/bitwig/flt/widget/library/dnd/SearchSourcePaletteInsertionPoint;LlYN;)LIgL;",
         at = At.REPLACE_CALL, callOwner = ZI.class, call = "mEL()I", id = "quick-sources-no-limit")
   public static int quickSourcesCount(ZI sources) {
      return 0;
   }

   /** VV.Anf(): a sidebar button's image. */
   @Hook(target = VV.class, method = "Anf()Ljava/net/URI;", at = At.ENTRY, id = "source-image-entry")
   public static void sourceImageEntry(@This VV source) {
      NODE.set(source);
   }

   @Hook(target = VV.class, method = "Anf()Ljava/net/URI;", at = At.WRAP_RETURN, id = "source-image")
   public static URI sourceImage(@Return URI bitwig) {
      URI mine = NocBrowser.sourceImage(take());
      return mine != null ? mine : bitwig;
   }

   /**
    * FL: the Location column. beI(Ek, boolean) decides whether an empty row is hidden; with a sidebar button
    * selected, the column only shows places that have something for it.
    */
   @Hook(target = FL.class, method = "hU_()V", at = At.REPLACE_CALL, callOwner = FL.class, call = "beI(LEk;Z)Z", id = "hide-empty-refresh")
   public static boolean hideEmpty(FL column, Ek node, boolean flag) {
      int mine = NocBrowser.hideEmpty(column, node);
      return mine >= 0 ? mine == 1 : column.beI(node, flag);
   }

   @Hook(target = FL.class, method = "unz(LEk;)Z", at = At.REPLACE_CALL, callOwner = FL.class, call = "beI(LEk;Z)Z", id = "hide-empty-check")
   public static boolean hideEmptyCheck(FL column, Ek node, boolean flag) {
      return hideEmpty(column, node, flag);
   }

   /** cFX.beI(...): a device's preset popup opens: in Everything, not Collections or Tracks. */
   @Hook(target = cFX.class, method = "beI(Lcom/bitwig/flt/document/core/iface/bMt;LJy;LaYN;LJy;Ljava/lang/Runnable;Ljava/lang/Runnable;Z)V",
         at = At.ENTRY, id = "popup-opening")
   public static void popupOpening(@Arg(1) Jy session, @Arg(2) aYN popup) {
      try {
         NocBrowser.popupOpening(session, popup);
      } catch (Throwable t) {
         NocBrowser.log("popup", t);
      }
   }

   // ================================================================== icons

   /** dou.beI(String, double): a window loads an icon by name; ours come from the mod's files. */
   @Hook(target = dou.class, method = "beI(Ljava/lang/String;D)Lcom/bitwig/graphics/haN;", at = At.ENTRY, id = "icons")
   public static void icon(@This dou window, @Arg(0) String name, @Arg(1) double scale) {
      Icons.loading(window, name, scale);
   }

   /** yO.BCC(): a collection folder's icon name. */
   @Hook(target = yO.class, method = "BCC()Ljava/lang/String;", at = At.ENTRY, id = "folder-icon-entry")
   public static void folderIconEntry(@This yO node) {
      NODE.set(node);
   }

   @Hook(target = yO.class, method = "BCC()Ljava/lang/String;", at = At.WRAP_RETURN, id = "folder-icon")
   public static String folderIcon(@Return String bitwig) {
      String mine = NocBrowser.treeIcon(take());
      return mine != null ? mine : bitwig;
   }

   /** Hh.BCC(): a collection's icon name. */
   @Hook(target = Hh.class, method = "BCC()Ljava/lang/String;", at = At.ENTRY, id = "collection-icon-entry")
   public static void collectionIconEntry(@This Hh node) {
      NODE.set(node);
   }

   @Hook(target = Hh.class, method = "BCC()Ljava/lang/String;", at = At.WRAP_RETURN, id = "collection-icon")
   public static String collectionIcon(@Return String bitwig) {
      String mine = NocBrowser.treeIcon(take());
      return mine != null ? mine : bitwig;
   }

   /** Hh.LYs(): a collection's image (its tile). */
   @Hook(target = Hh.class, method = "LYs()Ljava/net/URI;", at = At.ENTRY, id = "collection-image-entry")
   public static void collectionImageEntry(@This Hh node) {
      NODE.set(node);
   }

   @Hook(target = Hh.class, method = "LYs()Ljava/net/URI;", at = At.WRAP_RETURN, id = "collection-image")
   public static URI collectionImage(@Return URI bitwig) {
      URI mine = NocBrowser.nodeImage(take());
      return mine != null ? mine : bitwig;
   }

   // ================================================================== collections inside collections

   private static int children(Hh collection) {
      try {
         return collection.wLf().size(); // its child rows
      } catch (Throwable t) {
         return 0;
      }
   }

   private static boolean accepts(Ek node) {
      try {
         return NocCollectionFolders.accepts(node);
      } catch (Throwable t) {
         return false;
      }
   }

   // Hh (a collection) answers 0 children and "leaf" to Bitwig, so a collection can't hold collections. Its
   // real children are counted where Bitwig asks:
   @Hook(target = Eo.class, method = "beI(LKZ;Ljava/util/Set;)V", at = At.REPLACE_CALL, callOwner = KZ.class, call = "y3f()I", occurrence = 1, id = "child-count-eo-0")
   public static int childCount0(KZ node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = FL.class, method = "Gef(LFf;)V", at = At.REPLACE_CALL, callOwner = KZ.class, call = "y3f()I", occurrence = 1, id = "child-count-fl-1")
   public static int childCount1(KZ node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = Iy.class, method = "DIi()V", at = At.REPLACE_CALL, callOwner = Ek.class, call = "y3f()I", occurrence = 1, id = "child-count-iy-2")
   public static int childCount2(Ek node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = KZ.class, method = "beI(LEk;)V", at = At.REPLACE_CALL, callOwner = KZ.class, call = "y3f()I", occurrence = 1, id = "child-count-kz-3")
   public static int childCount3(KZ node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = KZ.class, method = "beI(LEk;)V", at = At.REPLACE_CALL, callOwner = KZ.class, call = "y3f()I", occurrence = 2, id = "child-count-kz-4")
   public static int childCount4(KZ node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = KZ.class, method = "beI(LEk;)V", at = At.REPLACE_CALL, callOwner = KZ.class, call = "y3f()I", occurrence = 3, id = "child-count-kz-5")
   public static int childCount5(KZ node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = KZ.class, method = "beI(LEk;)V", at = At.REPLACE_CALL, callOwner = KZ.class, call = "y3f()I", occurrence = 4, id = "child-count-kz-6")
   public static int childCount6(KZ node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = KZ.class, method = "beI(LEk;)V", at = At.REPLACE_CALL, callOwner = KZ.class, call = "y3f()I", occurrence = 5, id = "child-count-kz-7")
   public static int childCount7(KZ node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = KZ.class, method = "beI(Lui;)I", at = At.REPLACE_CALL, callOwner = KZ.class, call = "y3f()I", occurrence = 1, id = "child-count-kz-8")
   public static int childCount8(KZ node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = Ka.class, method = "beI(LEk;)V", at = At.REPLACE_CALL, callOwner = KZ.class, call = "y3f()I", occurrence = 1, id = "child-count-ka-9")
   public static int childCount9(KZ node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = La.class, method = "BBg(I)LEk;", at = At.REPLACE_CALL, callOwner = La.class, call = "y3f()I", occurrence = 1, id = "child-count-la-10")
   public static int childCount10(La node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = La.class, method = "MgQ(LEk;)V", at = At.REPLACE_CALL, callOwner = La.class, call = "y3f()I", occurrence = 1, id = "child-count-la-11")
   public static int childCount11(La node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = La.class, method = "beI(LEk;I)V", at = At.REPLACE_CALL, callOwner = La.class, call = "y3f()I", occurrence = 1, id = "child-count-la-12")
   public static int childCount12(La node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = Lc.class, method = "Gef(LKZ;I)I", at = At.REPLACE_CALL, callOwner = KZ.class, call = "y3f()I", occurrence = 1, id = "child-count-lc-13")
   public static int childCount13(KZ node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = CursorBrowserFilterItemProxy.class, method = "getFirstChild()LwT;", at = At.REPLACE_CALL, callOwner = xH.class, call = "y3f()I", occurrence = 1, id = "child-count-cursorbrowserfilteritemproxy-14")
   public static int childCount14(xH node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = CursorBrowserFilterItemProxy.class, method = "getFirstChild()LwT;", at = At.REPLACE_CALL, callOwner = xH.class, call = "y3f()I", occurrence = 2, id = "child-count-cursorbrowserfilteritemproxy-15")
   public static int childCount15(xH node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = CursorBrowserFilterItemProxy.class, method = "getLastChild()LwT;", at = At.REPLACE_CALL, callOwner = xH.class, call = "y3f()I", occurrence = 1, id = "child-count-cursorbrowserfilteritemproxy-16")
   public static int childCount16(xH node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = CursorBrowserFilterItemProxy.class, method = "getLastChild()LwT;", at = At.REPLACE_CALL, callOwner = xH.class, call = "y3f()I", occurrence = 2, id = "child-count-cursorbrowserfilteritemproxy-17")
   public static int childCount17(xH node) {
      return node instanceof Hh h ? children(h) : node.y3f();
   }

   @Hook(target = El.class, method = "beI(LEk;LEk;)I", at = At.REPLACE_CALL, callOwner = Ek.class, call = "Anf()Z", occurrence = 1, id = "leaf-el-0")
   public static boolean leaf0(Ek node) {
      return node instanceof Hh h ? children(h) == 0 : node.Anf();
   }

   @Hook(target = El.class, method = "beI(LEk;LEk;)I", at = At.REPLACE_CALL, callOwner = Ek.class, call = "Anf()Z", occurrence = 2, id = "leaf-el-1")
   public static boolean leaf1(Ek node) {
      return node instanceof Hh h ? children(h) == 0 : node.Anf();
   }

   @Hook(target = cFh.class, method = "Gef(Lui;)Z", at = At.REPLACE_CALL, callOwner = ui.class, call = "Anf()Z", occurrence = 1, id = "leaf-cfh-2")
   public static boolean leaf2(ui node) {
      return node instanceof Hh h ? children(h) == 0 : node.Anf();
   }

   @Hook(target = CD.class, method = "beI(Lui;)I", at = At.REPLACE_CALL, callOwner = LG.class, call = "beI(LEk;)Z", occurrence = 1, id = "accepts-cd-0")
   public static boolean accepts0(LG order, Ek node) {
      return order instanceof Fu && accepts(node) || order.beI(node);
   }

   @Hook(target = KZ.class, method = "beI(LEk;Z)LEk;", at = At.REPLACE_CALL, callOwner = LG.class, call = "beI(LEk;)Z", occurrence = 1, id = "accepts-kz-1")
   public static boolean accepts1(LG order, Ek node) {
      return order instanceof Fu && accepts(node) || order.beI(node);
   }

   @Hook(target = KZ.class, method = "MgQ(LEk;I)Z", at = At.REPLACE_CALL, callOwner = LG.class, call = "beI(LEk;)Z", occurrence = 1, id = "accepts-kz-2")
   public static boolean accepts2(LG order, Ek node) {
      return order instanceof Fu && accepts(node) || order.beI(node);
   }

   @Hook(target = KZ.class, method = "beI(Lui;)I", at = At.REPLACE_CALL, callOwner = LG.class, call = "beI(LEk;)Z", occurrence = 1, id = "accepts-kz-3")
   public static boolean accepts3(LG order, Ek node) {
      return order instanceof Fu && accepts(node) || order.beI(node);
   }

   /** Fu.compare: the Collections list's order (folders and collections). */
   @Hook(target = Fu.class, method = "compare(Ljava/lang/Object;Ljava/lang/Object;)I", at = At.REPLACE_CALL, callOwner = Fu.class,
         call = "beI(LKZ;LKZ;)I", id = "order")
   public static int order(Fu order, KZ a, KZ b) {
      int mine = Integer.MIN_VALUE;
      try {
         mine = NocCollectionFolders.compare(a, b);
      } catch (Throwable t) {
         NocBrowser.log("order", t);
      }
      return mine != Integer.MIN_VALUE ? mine : order.beI(a, b);
   }

   /** yO.nhI(): a copy of a folder row. */
   @Hook(target = yO.class, method = "nhI()LKZ;", at = At.ENTRY, id = "copy-folder-entry")
   public static void copyFolderEntry(@This yO node) {
      NODE.set(node);
   }

   @Hook(target = yO.class, method = "nhI()LKZ;", at = At.WRAP_RETURN, id = "copy-folder")
   public static Object copyFolder(@Return KZ bitwig) {
      KZ mine = NocCollectionFolders.copyFolder(take());
      return mine != null ? mine : bitwig;
   }

   /** KZ.hN_(): how a row orders its children. */
   @Hook(target = KZ.class, method = "hN_()LLG;", at = At.ENTRY, id = "node-order-entry")
   public static void nodeOrderEntry(@This KZ node) {
      NODE.set(node);
   }

   @Hook(target = KZ.class, method = "hN_()LLG;", at = At.WRAP_RETURN, id = "node-order")
   public static Object nodeOrder(@Return LG bitwig) {
      LG mine = NocCollectionFolders.nodeOrder(take());
      return mine != null ? mine : bitwig;
   }

   /** Hh.beI(EN): a collection's query (a parent collection shows what its children hold). */
   @Hook(target = Hh.class, method = "beI(LEN;)LFzK;", at = At.ENTRY, id = "parent-query-entry")
   public static void parentQueryEntry(@This Hh node) {
      NODE.set(node);
   }

   @Hook(target = Hh.class, method = "beI(LEN;)LFzK;", at = At.WRAP_RETURN, id = "parent-query")
   public static Object parentQuery(@Return FzK bitwig) {
      FzK mine = NocCollectionFolders.parentQuery(take());
      return mine != null ? mine : bitwig;
   }

   /**
    * Hc.beI(String): rename a collection. For a collection in a folder (or a parent), NocEdit renames it and
    * its folder; Bitwig's own file rename is then left out (unz is the check that starts it).
    */
   @Hook(target = Hc.class, method = "beI(Ljava/lang/String;)V", at = At.REPLACE_CALL, callOwner = Hc.class, call = "unz(Ljava/lang/String;)Z", id = "rename")
   public static boolean rename(Hc collection, String name) {
      String mine = null;
      try {
         mine = NocEdit.rename(collection, name);
      } catch (Throwable t) {
         NocBrowser.log("rename", t);
      }
      if (mine != null) {
         collection.sCA(mine); // set the name, as Bitwig's rename does at its end
         return false;         // skip Bitwig's own file rename
      }
      return collection.unz(name);
   }

   /** cDk: "New Collection" (EN.beI(name, colour)); a name with "/" creates it inside a folder. */
   @Hook(target = cDk.class, method = "beI(Leey;LdwF;LlsH;ZLJy;LEN;LJy;Lra;LdpW;I)Z", at = At.REPLACE_CALL, callOwner = EN.class,
         call = "beI(Ljava/lang/String;I)LGS;", id = "create-name")
   public static GS createCollection(EN collections, String name, int colour) {
      String mine = name;
      try {
         mine = NocEdit.createName(name);
      } catch (Throwable t) {
         NocBrowser.log("create", t);
      }
      return collections.beI(mine, colour);
   }

   /** cFm.Gef(uk, boolean): a browser tree view is set up. */
   @Hook(target = cFm.class, method = "Gef(Luk;Z)V", at = At.ENTRY, id = "tree-view-args")
   public static void treeViewArgs(@Arg(0) uk model) {
      NODE.set(model);
   }

   @Hook(target = cFm.class, method = "Gef(Luk;Z)V", at = At.BEFORE_RETURN, id = "tree-view")
   public static void treeView(@This cFm view) {
      try {
         NocEdit.treeView(view, take());
      } catch (Throwable t) {
         NocBrowser.log("tree view", t);
      }
   }

   /** cGK.beI(dMt, List): the browser's right-click menu. */
   @Hook(target = cGK.class, method = "beI(LdMt;Ljava/util/List;)V", at = At.ENTRY, id = "browser-menu-args")
   public static void browserMenuArgs(@Arg(0) dMt menu, @Arg(1) List<?> selection) {
      ARGS.set(new Object[]{menu, selection});
   }

   @Hook(target = cGK.class, method = "beI(LdMt;Ljava/util/List;)V", at = At.BEFORE_RETURN, id = "browser-menu")
   public static void browserMenu(@This cGK view) {
      Object[] a = ARGS.get();
      ARGS.remove();
      try {
         if (a != null) {
            NocEdit.menu(view, (dMt) a[0], (List) a[1]);
         }
      } catch (Throwable t) {
         NocBrowser.log("browser menu", t);
      }
   }

   // Eo.beI(KZ, Ek, KZ): a tree publishes its selected row after a rebuild; a row inside a folder is found again.

   @Hook(target = FL.class, method = "hU_()V", at = At.REPLACE_CALL, callOwner = FL.class, call = "beI(LKZ;LEk;LKZ;)V", id = "publish-selection-fl")
   public static void publishFl(FL tree, KZ root, Ek selected, KZ fresh) {
      tree.beI(root, publish(tree, selected, fresh), fresh);
   }

   @Hook(target = Hn.class, method = "hU_()V", at = At.REPLACE_CALL, callOwner = Hn.class, call = "beI(LKZ;LEk;LKZ;)V", id = "publish-selection-hn")
   public static void publishHn(Hn tree, KZ root, Ek selected, KZ fresh) {
      tree.beI(root, publish(tree, selected, fresh), fresh);
   }

   @Hook(target = Iy.class, method = "hU_()V", at = At.REPLACE_CALL, callOwner = Iy.class, call = "beI(LKZ;LEk;LKZ;)V", id = "publish-selection-iy")
   public static void publishIy(Iy tree, KZ root, Ek selected, KZ fresh) {
      tree.beI(root, publish(tree, selected, fresh), fresh);
   }

   private static Ek publish(Object tree, Ek selected, KZ fresh) {
      try {
         return NocCollectionFolders.publishSelection(tree, selected, fresh);
      } catch (Throwable t) {
         return selected;
      }
   }

   // ================================================================== folder rows in the Location column (FL)

   private static final ThreadLocal<Integer> INDEX = new ThreadLocal<>();
   private static final ThreadLocal<FL> COLUMN = new ThreadLocal<>();

   /** FL.beI(int, Hc): a collection row is added (to its folder, not the top). */
   @Hook(target = FL.class, method = "beI(ILHc;)V", at = At.ENTRY, id = "attach-index")
   public static void attachIndex(@Arg(0) int index) {
      INDEX.set(index);
   }

   @Hook(target = FL.class, method = "beI(ILHc;)V", at = At.REPLACE_CALL, callOwner = yO.class, call = "MgQ(LEk;)V", id = "attach")
   public static void attach(yO root, Ek node) {
      Integer i = INDEX.get();
      NocCollectionFolders.attach(root, node, i == null ? -1 : i);
   }

   /** FL.beI(jma): the column's rows are built. */
   @Hook(target = FL.class, method = "beI(Ljma;)V", at = At.REPLACE_CALL, callOwner = yO.class, call = "beI(Ljma;)V", id = "built")
   public static void built(yO root, jma query) {
      root.beI(query);
      NocCollectionFolders.built(root);
   }

   /** FL.yX3(): one search result counted against every collection; folders count it once. */
   @Hook(target = FL.class, method = "yX3()V", at = At.ENTRY, id = "count-entry")
   public static void countEntry(@This FL column) {
      COLUMN.set(column);
   }

   @Hook(target = FL.class, method = "yX3()V", at = At.REPLACE_CALL, callOwner = AKW.class, call = "BBg()Z", id = "count-hit")
   public static boolean countHit(AKW matcher) {
      boolean hit = matcher.BBg(); // the item is in this collection
      if (hit) {
         try {
            FL column = COLUMN.get();
            if (column != null) {
               AKW[] all = column.y3f; // one matcher per collection
               for (int i = 0; i < all.length; i++) {
                  if (all[i] == matcher) {
                     NocCollectionFolders.hit(column.nhI, i);
                     break;
                  }
               }
            }
         } catch (Throwable t) {
            NocBrowser.log("count", t);
         }
      }
      return hit;
   }

   @Hook(target = FL.class, method = "yX3()V", at = At.BEFORE_RETURN, id = "count-done")
   public static void countDone(@This FL column) {
      try {
         NocCollectionFolders.itemDone(column.nhI); // nhI: the column's root row
      } catch (Throwable t) {
         NocBrowser.log("count", t);
      }
   }

   /** FL.hU_(): the column refreshes its counts and drops what's empty. */
   @Hook(target = FL.class, method = "hU_()V", at = At.ENTRY, id = "refresh-entry")
   public static void refreshEntry(@This FL column) {
      COLUMN.set(column);
   }

   @Hook(target = FL.class, method = "hU_()V", at = At.REPLACE_CALL, callOwner = yO.class, call = "BBg(LEk;)V", id = "remove-child")
   public static void removeChild(yO root, Ek node) {
      NocCollectionFolders.removeChild(root, node);
   }

   @Hook(target = FL.class, method = "hU_()V", at = At.REPLACE_CALL, callOwner = yO.class, call = "byf(I)V", id = "refresh-finish")
   public static void refreshFinish(yO root, int count) {
      root.byf(count);
      FL column = COLUMN.get();
      if (column != null) {
         NocCollectionFolders.finish(column, root);
      }
   }

   /** Ha: the collections on disk; its constructor reads them. NocCollectionFolders reads them with folders. */
   @Hook(target = Ha.class, method = "<init>(LQly;)V", at = At.REPLACE_CALL, callOwner = Ha.class, call = "Gef()V", id = "load")
   public static void load(Ha collections) {
      NocCollectionFolders.load(collections.beI, collections.Gef); // the folder and the list it fills
   }

   // ================================================================== edits redraw once; each plugin once

   private static final ThreadLocal<Boolean> HOLD = new ThreadLocal<>();

   /** Jy.ppC(): a browser session schedules a refresh. Held while a collection edit is still going. */
   @Hook(target = Jy.class, method = "ppC()V", at = At.ENTRY, id = "hold-refresh-entry")
   public static void holdRefreshEntry(@This Jy session) {
      boolean hold = false;
      try {
         hold = NocEdit.holdRefresh(session);
      } catch (Throwable t) {
         NocBrowser.log("refresh", t);
      }
      HOLD.set(hold);
   }

   @Hook(target = Jy.class, method = "ppC()V", at = At.REPLACE_CALL, callOwner = tpA.class, call = "beI(LsyF;)V", id = "hold-refresh")
   public static void holdRefresh(tpA timer, syF task) {
      boolean hold = Boolean.TRUE.equals(HOLD.get());
      HOLD.remove();
      if (!hold) {
         timer.beI(task); // schedule it
      }
   }

   /** Jy.Fhl(): the browser's final query; each plugin once (see NocFormats). */
   @Hook(target = Jy.class, method = "Fhl()Ljma;", at = At.WRAP_RETURN, id = "formats")
   public static Object formats(@Return jma query) {
      try {
         return NocFormats.query(query);
      } catch (Throwable t) {
         return query;
      }
   }

   /** LSF.beI(jma, int, Smh): the result kinds are ordered. */
   @Hook(target = LSF.class, method = "beI(Ljma;ILSmh;)V", at = At.ENTRY, id = "kind-order")
   public static void kindOrder(@This LSF results) {
      try {
         NocFormats.order(results.Gef); // its kinds, in order
      } catch (Throwable t) {
         NocBrowser.log("kinds", t);
      }
   }

   // ================================================================== track presets

   /** bju: a track view widget made for a track view model. */
   @Hook(target = bju.class, method = "<init>", at = At.BEFORE_RETURN, id = "track-view")
   public static void trackView(@Arg(0) GRp view) {
      try {
         NocTracks.view(view);
      } catch (Throwable t) {
         NocBrowser.log("track view", t);
      }
   }

   /**
    * ra.Gef(): which result kinds a browser lists (a fixed set made in its constructor). The main browser (cCH)
    * also gets projects, so the Tracks button can list track presets (NocFormats keeps only those).
    */
   @Hook(target = ra.class, method = "Gef()Ljava/util/Set;", at = At.ENTRY, id = "browser-kinds-entry")
   public static void browserKindsEntry(@This ra browser) {
      NODE.set(browser);
   }

   @Hook(target = ra.class, method = "Gef()Ljava/util/Set;", at = At.WRAP_RETURN, id = "browser-kinds")
   @SuppressWarnings({"unchecked", "rawtypes"})
   public static Object browserKinds(@Return java.util.Set kinds) {
      Object b = take();
      if (!(b instanceof cCH) || kinds == null) {
         return kinds;
      }
      try {
         qV[] now = (qV[]) kinds.toArray(new qV[0]);
         qV[] want = NocTracks.kinds(now);
         if (want.length == now.length) {
            return kinds;
         }
         return KINDS.computeIfAbsent(kinds, k -> {
            java.util.Set out = new java.util.HashSet(k);
            out.addAll(java.util.Arrays.asList(want));
            return java.util.Collections.unmodifiableSet(out);
         });
      } catch (Throwable t) {
         NocBrowser.log("track presets: browser kinds", t);
         return kinds;
      }
   }

   private static final java.util.Map<java.util.Set, java.util.Set> KINDS =
      java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

   /** bnG.beI(dMt, dwF, bju, yAn): the track header right-click menu. */
   @Hook(target = bnG.class, method = "beI(LdMt;LdwF;Lbju;LyAn;)V", at = At.ENTRY, id = "track-menu-args")
   public static void trackMenuArgs(@Arg(0) dMt menu, @Arg(1) dwF owner, @Arg(2) bju header) {
      ARGS.set(new Object[]{menu, owner, header});
   }

   @Hook(target = bnG.class, method = "beI(LdMt;LdwF;Lbju;LyAn;)V", at = At.BEFORE_RETURN, id = "track-menu")
   public static void trackMenu() {
      Object[] a = ARGS.get();
      ARGS.remove();
      try {
         if (a != null) {
            NocTracks.menu((dMt) a[0], (dwF) a[1], (bju) a[2]);
         }
      } catch (Throwable t) {
         NocBrowser.log("track menu", t);
      }
   }

   /** baQ.beI(dMt, double, double, eqv): the arranger's background menu. */
   @Hook(target = baQ.class, method = "beI(LdMt;DDLeqv;)V", at = At.ENTRY, id = "area-menu-args")
   public static void areaMenuArgs(@Arg(0) dMt menu) {
      NODE.set(menu);
   }

   @Hook(target = baQ.class, method = "beI(LdMt;DDLeqv;)V", at = At.BEFORE_RETURN, id = "area-menu")
   public static void areaMenu(@This baQ area) {
      Object m = take();
      try {
         if (m instanceof dMt menu) {
            NocTracks.areaMenu(menu, area.vlg()); // vlg: its track view
         }
      } catch (Throwable t) {
         NocBrowser.log("area menu", t);
      }
   }

   /** baN.beI(dwF, dMt): the arranger's right-click menu (empty space and lanes). */
   @Hook(target = baN.class, method = "beI(LdwF;LdMt;)V", at = At.ENTRY, id = "arranger-menu-args")
   public static void arrangerMenuArgs(@Arg(1) dMt menu) {
      NODE.set(menu);
   }

   @Hook(target = baN.class, method = "beI(LdwF;LdMt;)V", at = At.BEFORE_RETURN, id = "arranger-menu")
   public static void arrangerMenu(@This baN arranger) {
      Object m = take();
      try {
         if (m instanceof dMt menu) {
            NocTracks.arrangerMenu(menu, arranger.dNa()); // dNa: its project
         }
      } catch (Throwable t) {
         NocBrowser.log("arranger menu", t);
      }
   }

   /** aNi.beI(dwF, dMt): the arranger timeline's right-click menu. */
   @Hook(target = aNi.class, method = "beI(LdwF;LdMt;)V", at = At.ENTRY, id = "lane-menu-args")
   public static void laneMenuArgs(@Arg(1) dMt menu) {
      NODE.set(menu);
   }

   @Hook(target = aNi.class, method = "beI(LdwF;LdMt;)V", at = At.BEFORE_RETURN, id = "lane-menu")
   public static void laneMenu(@This aNi timeline) {
      Object m = take();
      try {
         if (m instanceof dMt menu) {
            NocTracks.laneMenu(menu, timeline);
         }
      } catch (Throwable t) {
         NocBrowser.log("lane menu", t);
      }
   }

   /** bac.beI(dwF, dMt): a panel's background menu; ours goes right after "Add Group Track" (alv.Gef). */
   @Hook(target = bac.class, method = "beI(LdwF;LdMt;)V", at = At.REPLACE_CALL, callOwner = alv.class, call = "Gef(LdMt;LaCZ;Lalm;)V", id = "panel-menu")
   public static void panelMenu(alv items, dMt menu, aCZ panel, alm where) {
      items.Gef(menu, panel, where); // Bitwig's own items
      try {
         NocTracks.panelMenu(menu, panel);
      } catch (Throwable t) {
         NocBrowser.log("panel menu", t);
      }
   }

   /** rmj.Gef(Object, Awj): a dropped browser item; a one-track preset adds that track, not a "Project" group. */
   @Hook(target = rmj.class, method = "Gef(Ljava/lang/Object;Lcom/bitwig/flt/document/core/master/Awj;)Lcom/bitwig/flt/document/core/master/sJU;",
         at = At.ENTRY, id = "drop-args")
   public static void dropArgs(@Arg(0) Object item, @Arg(1) Awj target) {
      DROP.set(new Object[]{item, target});
      try {
         NocBrowser.log("Track presets: drop reached (" + (item == null ? "null" : item.getClass().getName()) + ", "
            + (target == null ? "no file" : String.valueOf(target.beI())) + ")", null);
      } catch (Throwable ignored) {
      }
   }

   private static final ThreadLocal<Object[]> DROP = new ThreadLocal<>();

   /**
    * The file's sub-item (Lpf.MgQ(): which track of the project, as an index; empty = the whole project as a
    * group). A one-track preset with no sub-item reads as "0", so Bitwig's own code adds just that track.
    * (WRAP_RETURN below only sees the method's last return, not the "whole project" one.)
    */
   @Hook(target = rmj.class, method = "Gef(Ljava/lang/Object;Lcom/bitwig/flt/document/core/master/Awj;)Lcom/bitwig/flt/document/core/master/sJU;",
         at = At.REPLACE_CALL, callOwner = Lpf.class, call = "MgQ()Ljava/lang/String;", id = "drop-track-index")
   public static String dropTrackIndex(Lpf file) {
      String sub = file.MgQ();
      try {
         Object[] a = DROP.get();
         if ((sub == null || sub.isEmpty()) && a != null && a[0] instanceof CmH doc) {
            String index = NocTracks.dropIndex(doc, file);
            if (index != null) {
               return index;
            }
         }
      } catch (Throwable t) {
         NocBrowser.log("Track presets: drop index", t);
      }
      return sub;
   }

   @Hook(target = rmj.class, method = "Gef(Ljava/lang/Object;Lcom/bitwig/flt/document/core/master/Awj;)Lcom/bitwig/flt/document/core/master/sJU;",
         at = At.WRAP_RETURN, id = "drop-track")
   public static Object dropTrack(@Return sJU result) {
      Object[] a = DROP.get();
      DROP.remove();
      if (a == null) {
         NocBrowser.log("Track presets: drop hook ran without its arguments", null);
      }
      try {
         if (a != null) {
            Object mine = NocTracks.dropped(result, a[0], a[1]);
            if (mine instanceof sJU s) {
               return s;
            }
         }
      } catch (Throwable t) {
         NocBrowser.log("drop", t);
      }
      return result;
   }
}
