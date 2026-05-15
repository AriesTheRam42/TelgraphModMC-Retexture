package com.reis.telegraph.registration;

import com.reis.telegraph.items.CableCutterItem;
import com.reis.telegraph.items.DynamiteItem;
import com.reis.telegraph.items.TelegraphMessageItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, "telegraph");

    public static final RegistryObject<Item> TELEGRAM =
            ITEMS.register("telegram", TelegraphMessageItem::new);

    public static final RegistryObject<Item> CABLE_CUTTER =
            ITEMS.register("cable_cutter", CableCutterItem::new);

    public static final RegistryObject<Item> DYNAMITE =
            ITEMS.register("dynamite", DynamiteItem::new);
}
