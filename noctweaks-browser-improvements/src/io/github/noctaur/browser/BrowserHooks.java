package io.github.noctaur.browser;

import bitwig.obf.*;
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;
import com.bitwig.flt.control_surface.proxy.CursorBrowserFilterItemProxy; // the browser as controller scripts see it
import com.bitwig.flt.document.core.iface.pHd;                           // a project document

import java.net.URI;
import java.util.List;

/**
 * Every hook of Browser: Collections and Icons. The logic is in the Noc* classes.
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
   @Hook(target = Vv.class, method = "<init>", at = At.BEFORE_RETURN, id = "sources")
   public static void sources(@This Vv list) {
      try {
         list.cfM(NocBrowser.source()); // add a source
      } catch (Throwable t) {
         NocBrowser.log("could not add the Collections button", t);
      }
   }

   /** g3b.beI(qrL): a sidebar button's label. */
   @Hook(target = xJ.class, method = "cfM(LWyZ;)Ljava/lang/String;", at = At.ENTRY, id = "label-entry")
   public static void labelEntry(@This xJ button) {
      NODE.set(button);
   }

   @Hook(target = xJ.class, method = "cfM(LWyZ;)Ljava/lang/String;", at = At.WRAP_RETURN, id = "label")
   public static String label(@Return String bitwig) {
      try {
         Object b = take();
         String mine = b instanceof xJ g ? NocBrowser.label(g.Hnt) : null; // sCA: the button's source
         return mine != null ? mine : bitwig;
      } catch (Throwable t) {
         return bitwig;
      }
   }

   /** cCE.Bqk(): the sidebar's pinned buttons. Pin Collections and Tracks once. */
   private static final ThreadLocal<Object> PIN_OWNER = new ThreadLocal<>();

   @Hook(target = cBh.class, method = "lYc()LYO;", at = At.ENTRY, id = "pin-entry")
   public static void pinEntry(@This cBh browser) {
      PIN_OWNER.set(browser);
   }

   /** Tracks goes on the main browser's sidebar only (cCH); popups (cCL, cFT) keep their own buttons. */
   @Hook(target = cBh.class, method = "lYc()LYO;", at = At.WRAP_RETURN, id = "pin")
   public static Object pin(@Return YO pinned) {
      Object owner = PIN_OWNER.get();
      PIN_OWNER.remove();
      NocBrowser.ensurePinned(pinned, owner instanceof cBj);
      return pinned;
   }

   /** Jy.beI(WV, boolean): a browser session switches to another sidebar button. */
   @Hook(target = Jp.class, method = "cfM(LWl;Z)V", at = At.BEFORE_RETURN, id = "source-chosen")
   public static void sourceChosen(@This Jp session) {
      NocBrowser.sourceChosen(session);
   }

   /**
    * ZI: the quick sources. Dragging a button onto a full row (12) replaces one; with the count read as 0 here,
    * a drag adds it instead. (Pin to Quick Sources in a collection's menu has no limit either.)
    */
   @Hook(target = YO.class, method = "cfM(Lcom/bitwig/flt/widget/library/dnd/MVg;Lcom/bitwig/flt/widget/library/dnd/SearchSourcePaletteInsertionPoint;LXuI;)LQbl;",
         at = At.REPLACE_CALL, callOwner = YO.class, call = "tp2()I", id = "quick-sources-no-limit")
   public static int quickSourcesCount(YO sources) {
      return 0;
   }

   /** VV.Anf(): a sidebar button's image. */
   @Hook(target = Vn.class, method = "hUR()Ljava/net/URI;", at = At.ENTRY, id = "source-image-entry")
   public static void sourceImageEntry(@This Vn source) {
      NODE.set(source);
   }

   @Hook(target = Vn.class, method = "hUR()Ljava/net/URI;", at = At.WRAP_RETURN, id = "source-image")
   public static URI sourceImage(@Return URI bitwig) {
      URI mine = NocBrowser.sourceImage(take());
      return mine != null ? mine : bitwig;
   }

   /**
    * FL: the Location column. beI(Ek, boolean) decides whether an empty row is hidden; with a sidebar button
    * selected, the column only shows places that have something for it.
    */
   @Hook(target = FE.class, method = "hT_()V", at = At.REPLACE_CALL, callOwner = FE.class, call = "cfM(LDZ;Z)Z", id = "hide-empty-refresh")
   public static boolean hideEmpty(FE column, DZ node, boolean flag) {
      int mine = NocBrowser.hideEmpty(column, node);
      return mine >= 0 ? mine == 1 : column.cfM(node, flag);
   }

   @Hook(target = FE.class, method = "UNj(LDZ;)Z", at = At.REPLACE_CALL, callOwner = FE.class, call = "cfM(LDZ;Z)Z", id = "hide-empty-check")
   public static boolean hideEmptyCheck(FE column, DZ node, boolean flag) {
      return hideEmpty(column, node, flag);
   }

   /** cFX.beI(...): a device's preset popup opens: in Everything, not Collections or Tracks. */
   @Hook(target = cEz.class, method = "cfM(Lcom/bitwig/flt/document/core/iface/pHd;LJp;LaXu;LJp;Ljava/lang/Runnable;Ljava/lang/Runnable;Z)V",
         at = At.ENTRY, id = "popup-opening")
   public static void popupOpening(@Arg(1) Jp session, @Arg(2) aXu popup) {
      try {
         NocBrowser.popupOpening(session, popup);
      } catch (Throwable t) {
         NocBrowser.log("popup", t);
      }
   }

   // ================================================================== icons

   /** dou.beI(String, double): a window loads an icon by name; ours come from the mod's files. */
   @Hook(target = dnf.class, method = "cfM(Ljava/lang/String;D)Lcom/bitwig/graphics/BrJ;", at = At.ENTRY, id = "icons")
   public static void icon(@This dnf window, @Arg(0) String name, @Arg(1) double scale) {
      Icons.loading(window, name, scale);
   }

   /** yO.BCC(): a collection folder's icon name. */
   @Hook(target = yF.class, method = "Ucl()Ljava/lang/String;", at = At.ENTRY, id = "folder-icon-entry")
   public static void folderIconEntry(@This yF node) {
      NODE.set(node);
   }

   @Hook(target = yF.class, method = "Ucl()Ljava/lang/String;", at = At.WRAP_RETURN, id = "folder-icon")
   public static String folderIcon(@Return String bitwig) {
      String mine = NocBrowser.treeIcon(take());
      return mine != null ? mine : bitwig;
   }

   /** Hh.BCC(): a collection's icon name. */
   @Hook(target = GW.class, method = "Ucl()Ljava/lang/String;", at = At.ENTRY, id = "collection-icon-entry")
   public static void collectionIconEntry(@This GW node) {
      NODE.set(node);
   }

   @Hook(target = GW.class, method = "Ucl()Ljava/lang/String;", at = At.WRAP_RETURN, id = "collection-icon")
   public static String collectionIcon(@Return String bitwig) {
      String mine = NocBrowser.treeIcon(take());
      return mine != null ? mine : bitwig;
   }

   /** Hh.LYs(): a collection's image (its tile). */
   @Hook(target = GW.class, method = "MIO()Ljava/net/URI;", at = At.ENTRY, id = "collection-image-entry")
   public static void collectionImageEntry(@This GW node) {
      NODE.set(node);
   }

   @Hook(target = GW.class, method = "MIO()Ljava/net/URI;", at = At.WRAP_RETURN, id = "collection-image")
   public static URI collectionImage(@Return URI bitwig) {
      URI mine = NocBrowser.nodeImage(take());
      return mine != null ? mine : bitwig;
   }

   // ================================================================== collections inside collections

   private static int children(GW collection) {
      try {
         return collection.hbP().size(); // its child rows
      } catch (Throwable t) {
         return 0;
      }
   }

   private static boolean accepts(DZ node) {
      try {
         return NocCollectionFolders.accepts(node);
      } catch (Throwable t) {
         return false;
      }
   }

   // Hh (a collection) answers 0 children and "leaf" to Bitwig, so a collection can't hold collections. Its
   // real children are counted where Bitwig asks:
   @Hook(target = Ef.class, method = "cfM(LKI;Ljava/util/Set;)V", at = At.REPLACE_CALL, callOwner = KI.class, call = "uWE()I", occurrence = 1, id = "child-count-eo-0")
   public static int childCount0(KI node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = FE.class, method = "MRY(LEX;)V", at = At.REPLACE_CALL, callOwner = KI.class, call = "uWE()I", occurrence = 1, id = "child-count-fl-1")
   public static int childCount1(KI node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = Ij.class, method = "eej()V", at = At.REPLACE_CALL, callOwner = DZ.class, call = "uWE()I", occurrence = 1, id = "child-count-iy-2")
   public static int childCount2(DZ node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = KI.class, method = "cfM(LDZ;)V", at = At.REPLACE_CALL, callOwner = KI.class, call = "uWE()I", occurrence = 1, id = "child-count-kz-3")
   public static int childCount3(KI node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = KI.class, method = "cfM(LDZ;)V", at = At.REPLACE_CALL, callOwner = KI.class, call = "uWE()I", occurrence = 2, id = "child-count-kz-4")
   public static int childCount4(KI node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = KI.class, method = "cfM(LDZ;)V", at = At.REPLACE_CALL, callOwner = KI.class, call = "uWE()I", occurrence = 3, id = "child-count-kz-5")
   public static int childCount5(KI node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = KI.class, method = "cfM(LDZ;)V", at = At.REPLACE_CALL, callOwner = KI.class, call = "uWE()I", occurrence = 4, id = "child-count-kz-6")
   public static int childCount6(KI node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = KI.class, method = "cfM(LDZ;)V", at = At.REPLACE_CALL, callOwner = KI.class, call = "uWE()I", occurrence = 5, id = "child-count-kz-7")
   public static int childCount7(KI node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = KI.class, method = "cfM(LtJ;)I", at = At.REPLACE_CALL, callOwner = KI.class, call = "uWE()I", occurrence = 1, id = "child-count-kz-8")
   public static int childCount8(KI node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = JI.class, method = "cfM(LDZ;)V", at = At.REPLACE_CALL, callOwner = KI.class, call = "uWE()I", occurrence = 1, id = "child-count-ka-9")
   public static int childCount9(KI node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = KJ.class, method = "IwV(I)LDZ;", at = At.REPLACE_CALL, callOwner = KJ.class, call = "uWE()I", occurrence = 1, id = "child-count-la-10")
   public static int childCount10(KJ node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = KJ.class, method = "FzB(LDZ;)V", at = At.REPLACE_CALL, callOwner = KJ.class, call = "uWE()I", occurrence = 1, id = "child-count-la-11")
   public static int childCount11(KJ node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = KJ.class, method = "cfM(LDZ;I)V", at = At.REPLACE_CALL, callOwner = KJ.class, call = "uWE()I", occurrence = 1, id = "child-count-la-12")
   public static int childCount12(KJ node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = KK.class, method = "MRY(LKI;I)I", at = At.REPLACE_CALL, callOwner = KI.class, call = "uWE()I", occurrence = 1, id = "child-count-lc-13")
   public static int childCount13(KI node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = CursorBrowserFilterItemProxy.class, method = "getFirstChild()Lwv;", at = At.REPLACE_CALL, callOwner = xl.class, call = "uWE()I", occurrence = 1, id = "child-count-cursorbrowserfilteritemproxy-14")
   public static int childCount14(xl node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = CursorBrowserFilterItemProxy.class, method = "getFirstChild()Lwv;", at = At.REPLACE_CALL, callOwner = xl.class, call = "uWE()I", occurrence = 2, id = "child-count-cursorbrowserfilteritemproxy-15")
   public static int childCount15(xl node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = CursorBrowserFilterItemProxy.class, method = "getLastChild()Lwv;", at = At.REPLACE_CALL, callOwner = xl.class, call = "uWE()I", occurrence = 1, id = "child-count-cursorbrowserfilteritemproxy-16")
   public static int childCount16(xl node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = CursorBrowserFilterItemProxy.class, method = "getLastChild()Lwv;", at = At.REPLACE_CALL, callOwner = xl.class, call = "uWE()I", occurrence = 2, id = "child-count-cursorbrowserfilteritemproxy-17")
   public static int childCount17(xl node) {
      return node instanceof GW h ? children(h) : node.uWE();
   }

   @Hook(target = Ea.class, method = "cfM(LDZ;LDZ;)I", at = At.REPLACE_CALL, callOwner = DZ.class, call = "hUR()Z", occurrence = 1, id = "leaf-el-0")
   public static boolean leaf0(DZ node) {
      return node instanceof GW h ? children(h) == 0 : node.hUR();
   }

   @Hook(target = Ea.class, method = "cfM(LDZ;LDZ;)I", at = At.REPLACE_CALL, callOwner = DZ.class, call = "hUR()Z", occurrence = 2, id = "leaf-el-1")
   public static boolean leaf1(DZ node) {
      return node instanceof GW h ? children(h) == 0 : node.hUR();
   }

   @Hook(target = cDJ.class, method = "MRY(LtJ;)Z", at = At.REPLACE_CALL, callOwner = tJ.class, call = "hUR()Z", occurrence = 1, id = "leaf-cfh-2")
   public static boolean leaf2(tJ node) {
      return node instanceof GW h ? children(h) == 0 : node.hUR();
   }

   @Hook(target = Ct.class, method = "cfM(LtJ;)I", at = At.REPLACE_CALL, callOwner = Lm.class, call = "cfM(LDZ;)Z", occurrence = 1, id = "accepts-cd-0")
   public static boolean accepts0(Lm order, DZ node) {
      return order instanceof Fk && accepts(node) || order.cfM(node);
   }

   @Hook(target = KI.class, method = "cfM(LDZ;Z)LDZ;", at = At.REPLACE_CALL, callOwner = Lm.class, call = "cfM(LDZ;)Z", occurrence = 1, id = "accepts-kz-1")
   public static boolean accepts1(Lm order, DZ node) {
      return order instanceof Fk && accepts(node) || order.cfM(node);
   }

   @Hook(target = KI.class, method = "FzB(LDZ;I)Z", at = At.REPLACE_CALL, callOwner = Lm.class, call = "cfM(LDZ;)Z", occurrence = 1, id = "accepts-kz-2")
   public static boolean accepts2(Lm order, DZ node) {
      return order instanceof Fk && accepts(node) || order.cfM(node);
   }

   @Hook(target = KI.class, method = "cfM(LtJ;)I", at = At.REPLACE_CALL, callOwner = Lm.class, call = "cfM(LDZ;)Z", occurrence = 1, id = "accepts-kz-3")
   public static boolean accepts3(Lm order, DZ node) {
      return order instanceof Fk && accepts(node) || order.cfM(node);
   }

   /** Fu.compare: the Collections list's order (folders and collections). */
   @Hook(target = Fk.class, method = "compare(Ljava/lang/Object;Ljava/lang/Object;)I", at = At.REPLACE_CALL, callOwner = Fk.class,
         call = "cfM(LKI;LKI;)I", id = "order")
   public static int order(Fk order, KI a, KI b) {
      int mine = Integer.MIN_VALUE;
      try {
         mine = NocCollectionFolders.compare(a, b);
      } catch (Throwable t) {
         NocBrowser.log("order", t);
      }
      return mine != Integer.MIN_VALUE ? mine : order.cfM(a, b);
   }

   /** yO.nhI(): a copy of a folder row. */
   @Hook(target = yF.class, method = "Z1C()LKI;", at = At.ENTRY, id = "copy-folder-entry")
   public static void copyFolderEntry(@This yF node) {
      NODE.set(node);
   }

   @Hook(target = yF.class, method = "Z1C()LKI;", at = At.WRAP_RETURN, id = "copy-folder")
   public static Object copyFolder(@Return KI bitwig) {
      KI mine = NocCollectionFolders.copyFolder(take());
      return mine != null ? mine : bitwig;
   }

   /** KZ.hN_(): how a row orders its children. */
   @Hook(target = KI.class, method = "hM_()LLm;", at = At.ENTRY, id = "node-order-entry")
   public static void nodeOrderEntry(@This KI node) {
      NODE.set(node);
   }

   @Hook(target = KI.class, method = "hM_()LLm;", at = At.WRAP_RETURN, id = "node-order")
   public static Object nodeOrder(@Return Lm bitwig) {
      Lm mine = NocCollectionFolders.nodeOrder(take());
      return mine != null ? mine : bitwig;
   }

   /** Hh.beI(EN): a collection's query (a parent collection shows what its children hold). */
   @Hook(target = GW.class, method = "cfM(LEE;)LohJ;", at = At.ENTRY, id = "parent-query-entry")
   public static void parentQueryEntry(@This GW node) {
      NODE.set(node);
   }

   @Hook(target = GW.class, method = "cfM(LEE;)LohJ;", at = At.WRAP_RETURN, id = "parent-query")
   public static Object parentQuery(@Return ohJ bitwig) {
      ohJ mine = NocCollectionFolders.parentQuery(take());
      return mine != null ? mine : bitwig;
   }

   /**
    * Hc.beI(String): rename a collection. For a collection in a folder (or a parent), NocEdit renames it and
    * its folder; Bitwig's own file rename is then left out (unz is the check that starts it).
    */
   @Hook(target = GT.class, method = "cfM(Ljava/lang/String;)V", at = At.REPLACE_CALL, callOwner = GT.class, call = "UNj(Ljava/lang/String;)Z", id = "rename")
   public static boolean rename(GT collection, String name) {
      String mine = null;
      try {
         mine = NocEdit.rename(collection, name);
      } catch (Throwable t) {
         NocBrowser.log("rename", t);
      }
      if (mine != null) {
         collection.Hnt(mine); // set the name, as Bitwig's rename does at its end
         return false;         // skip Bitwig's own file rename
      }
      return collection.UNj(name);
   }

   /** cDk: "New Collection" (EN.beI(name, colour)); a name with "/" creates it inside a folder. */
   @Hook(target = cBP.class, method = "cfM(LecV;Ldve;LfIx;ZLJp;LEE;LJp;LqH;LdoH;I)Z", at = At.REPLACE_CALL, callOwner = EE.class,
         call = "cfM(Ljava/lang/String;I)LGK;", id = "create-name")
   public static GK createCollection(EE collections, String name, int colour) {
      String mine = name;
      try {
         mine = NocEdit.createName(name);
      } catch (Throwable t) {
         NocBrowser.log("create", t);
      }
      return collections.cfM(mine, colour);
   }

   /** cFm.Gef(uk, boolean): a browser tree view is set up. */
   @Hook(target = cDO.class, method = "MRY(LtN;Z)V", at = At.ENTRY, id = "tree-view-args")
   public static void treeViewArgs(@Arg(0) tN model) {
      NODE.set(model);
   }

   @Hook(target = cDO.class, method = "MRY(LtN;Z)V", at = At.BEFORE_RETURN, id = "tree-view")
   public static void treeView(@This cDO view) {
      try {
         NocEdit.treeView(view, take());
      } catch (Throwable t) {
         NocBrowser.log("tree view", t);
      }
   }

   /** cGK.beI(dMt, List): the browser's right-click menu. */
   @Hook(target = cFm.class, method = "cfM(LdKW;Ljava/util/List;)V", at = At.ENTRY, id = "browser-menu-args")
   public static void browserMenuArgs(@Arg(0) dKW menu, @Arg(1) List<?> selection) {
      ARGS.set(new Object[]{menu, selection});
   }

   @Hook(target = cFm.class, method = "cfM(LdKW;Ljava/util/List;)V", at = At.BEFORE_RETURN, id = "browser-menu")
   public static void browserMenu(@This cFm view) {
      Object[] a = ARGS.get();
      ARGS.remove();
      try {
         if (a != null) {
            NocEdit.menu(view, (dKW) a[0], (List) a[1]);
         }
      } catch (Throwable t) {
         NocBrowser.log("browser menu", t);
      }
   }

   // Eo.beI(KZ, Ek, KZ): a tree publishes its selected row after a rebuild; a row inside a folder is found again.

   @Hook(target = FE.class, method = "hT_()V", at = At.REPLACE_CALL, callOwner = FE.class, call = "cfM(LKI;LDZ;LKI;)V", id = "publish-selection-fl")
   public static void publishFl(FE tree, KI root, DZ selected, KI fresh) {
      tree.cfM(root, publish(tree, selected, fresh), fresh);
   }

   @Hook(target = Hb.class, method = "hT_()V", at = At.REPLACE_CALL, callOwner = Hb.class, call = "cfM(LKI;LDZ;LKI;)V", id = "publish-selection-hn")
   public static void publishHn(Hb tree, KI root, DZ selected, KI fresh) {
      tree.cfM(root, publish(tree, selected, fresh), fresh);
   }

   @Hook(target = Ij.class, method = "hT_()V", at = At.REPLACE_CALL, callOwner = Ij.class, call = "cfM(LKI;LDZ;LKI;)V", id = "publish-selection-iy")
   public static void publishIy(Ij tree, KI root, DZ selected, KI fresh) {
      tree.cfM(root, publish(tree, selected, fresh), fresh);
   }

   private static DZ publish(Object tree, DZ selected, KI fresh) {
      try {
         return NocCollectionFolders.publishSelection(tree, selected, fresh);
      } catch (Throwable t) {
         return selected;
      }
   }

   // ================================================================== folder rows in the Location column (FL)

   private static final ThreadLocal<Integer> INDEX = new ThreadLocal<>();
   private static final ThreadLocal<FE> COLUMN = new ThreadLocal<>();

   /** FL.beI(int, Hc): a collection row is added (to its folder, not the top). */
   @Hook(target = FE.class, method = "cfM(ILGT;)V", at = At.ENTRY, id = "attach-index")
   public static void attachIndex(@Arg(0) int index) {
      INDEX.set(index);
   }

   @Hook(target = FE.class, method = "cfM(ILGT;)V", at = At.REPLACE_CALL, callOwner = yF.class, call = "FzB(LDZ;)V", id = "attach")
   public static void attach(yF root, DZ node) {
      Integer i = INDEX.get();
      NocCollectionFolders.attach(root, node, i == null ? -1 : i);
   }

   /** FL.beI(jma): the column's rows are built. */
   @Hook(target = FE.class, method = "cfM(LM22;)V", at = At.REPLACE_CALL, callOwner = yF.class, call = "cfM(LM22;)V", id = "built")
   public static void built(yF root, M22 query) {
      root.cfM(query);
      NocCollectionFolders.built(root);
   }

   /** FL.yX3(): one search result counted against every collection; folders count it once. */
   @Hook(target = FE.class, method = "RDJ()V", at = At.ENTRY, id = "count-entry")
   public static void countEntry(@This FE column) {
      COLUMN.set(column);
   }

   @Hook(target = FE.class, method = "RDJ()V", at = At.REPLACE_CALL, callOwner = nCp.class, call = "IwV()Z", id = "count-hit")
   public static boolean countHit(nCp matcher) {
      boolean hit = matcher.IwV(); // the item is in this collection
      if (hit) {
         try {
            FE column = COLUMN.get();
            if (column != null) {
               nCp[] all = column.uWE; // one matcher per collection
               for (int i = 0; i < all.length; i++) {
                  if (all[i] == matcher) {
                     NocCollectionFolders.hit(column.Z1C, i);
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

   @Hook(target = FE.class, method = "RDJ()V", at = At.BEFORE_RETURN, id = "count-done")
   public static void countDone(@This FE column) {
      try {
         NocCollectionFolders.itemDone(column.Z1C); // nhI: the column's root row
      } catch (Throwable t) {
         NocBrowser.log("count", t);
      }
   }

   /** FL.hU_(): the column refreshes its counts and drops what's empty. */
   @Hook(target = FE.class, method = "hT_()V", at = At.ENTRY, id = "refresh-entry")
   public static void refreshEntry(@This FE column) {
      COLUMN.set(column);
   }

   @Hook(target = FE.class, method = "hT_()V", at = At.REPLACE_CALL, callOwner = yF.class, call = "IwV(LDZ;)V", id = "remove-child")
   public static void removeChild(yF root, DZ node) {
      NocCollectionFolders.removeChild(root, node);
   }

   @Hook(target = FE.class, method = "hT_()V", at = At.REPLACE_CALL, callOwner = yF.class, call = "TMF(I)V", id = "refresh-finish")
   public static void refreshFinish(yF root, int count) {
      root.TMF(count);
      FE column = COLUMN.get();
      if (column != null) {
         NocCollectionFolders.finish(column, root);
      }
   }

   /** Ha: the collections on disk; its constructor reads them. NocCollectionFolders reads them with folders. */
   @Hook(target = GS.class, method = "<init>(LPRZ;)V", at = At.REPLACE_CALL, callOwner = GS.class, call = "MRY()V", id = "load")
   public static void load(GS collections) {
      NocCollectionFolders.load(collections.cfM, collections.MRY); // the folder and the list it fills
   }

   // ================================================================== edits redraw once; each plugin once

   private static final ThreadLocal<Boolean> HOLD = new ThreadLocal<>();

   /** Jy.ppC(): a browser session schedules a refresh. Held while a collection edit is still going. */
   @Hook(target = Jp.class, method = "DxJ()V", at = At.ENTRY, id = "hold-refresh-entry")
   public static void holdRefreshEntry(@This Jp session) {
      boolean hold = false;
      try {
         hold = NocEdit.holdRefresh(session);
      } catch (Throwable t) {
         NocBrowser.log("refresh", t);
      }
      HOLD.set(hold);
   }

   @Hook(target = Jp.class, method = "DxJ()V", at = At.REPLACE_CALL, callOwner = aRr.class, call = "cfM(Lgsm;)V", id = "hold-refresh")
   public static void holdRefresh(aRr timer, gsm task) {
      boolean hold = Boolean.TRUE.equals(HOLD.get());
      HOLD.remove();
      if (!hold) {
         timer.cfM(task); // schedule it
      }
   }

   /** Jy.Fhl(): the browser's final query; each plugin once (see NocFormats). */
   @Hook(target = Jp.class, method = "tSS()LM22;", at = At.WRAP_RETURN, id = "formats")
   public static Object formats(@Return M22 query) {
      try {
         return NocFormats.query(query);
      } catch (Throwable t) {
         return query;
      }
   }

   /** LSF.beI(jma, int, Smh): the result kinds are ordered. */
   @Hook(target = Wix.class, method = "cfM(LM22;ILsTA;)V", at = At.ENTRY, id = "kind-order")
   public static void kindOrder(@This Wix results) {
      try {
         NocFormats.order(results.MRY); // its kinds, in order
      } catch (Throwable t) {
         NocBrowser.log("kinds", t);
      }
   }
}
