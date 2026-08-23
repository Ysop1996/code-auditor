package de.lifeos.core.runtime

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32

/**
 * DEX TEMPLATE ASSEMBLER — Erzeugt minimale, valide DEX-Bytecode-Dateien
 * für dynamische Werkzeug-Module zur Laufzeit.
 *
 * Generiert eine Klasse, die DynamicPluginModule implementiert, mit einer
 * executeAction-Methode, die eine HashMap zurückgibt.
 *
 * Vektoren:
 * - [EXP-SYNTH] Template-basierte DEX-Assemblierung ohne externe Tools
 * - [SEC-RAM] Zero-Disk-Trace: Generierung erfolgt ausschließlich im RAM
 */
object DexTemplateAssembler {

    /**
     * Erzeugt eine minimale DEX-Datei für ein Werkzeug-Modul.
     */
    fun assembleModuleDex(entryClassName: String): ByteArray {
        val strings = mutableListOf<String>()
        val typeIds = mutableListOf<Int>()
        val protoIds = mutableListOf<ProtoId>()
        val methodIds = mutableListOf<MethodId>()
        val classDefs = mutableListOf<ClassDef>()

        // String-Pool aufbauen
        val stringSuperclass = "java.lang.Object"
        val stringInterface = "de.lifeos.core.runtime.DynamicPluginModule"
        val stringHashMap = "java.util.HashMap"
        val stringMap = "java.util.Map"
        val stringObject = "java.lang.Object"
        val stringMethodName = "executeAction"
        val stringSignature = "(Ljava/util/Map;)Ljava/util/Map;"
        val stringInit = "<init>"
        val stringInitSig = "()V"
        val stringPut = "put"
        val stringPutSig = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"

        strings.addAll(listOf(
            entryClassName,
            stringSuperclass,
            stringInterface,
            stringHashMap,
            stringMap,
            stringObject,
            stringMethodName,
            stringSignature,
            stringInit,
            stringInitSig,
            stringPut,
            stringPutSig
        ))

        // String-IDs zuordnen
        fun stringIndex(s: String): Int = strings.indexOf(s).coerceAtLeast(0)

        // Type-IDs
        val typeClass = 0 // entryClassName
        val typeSuperclass = 1 // java.lang.Object
        val typeInterface = 2 // DynamicPluginModule
        val typeHashMap = 3 // java.util.HashMap
        val typeMap = 4 // java.util.Map
        val typeObject = 5 // java.lang.Object

        typeIds.addAll(listOf(typeClass, typeSuperclass, typeInterface, typeHashMap, typeMap, typeObject))

        // Proto-IDs
        val protoInit = 0 // ()V
        val protoExecute = 1 // (Ljava/util/Map;)Ljava/util/Map;

        protoIds.addAll(listOf(
            ProtoId(stringIndex(stringInitSig), typeVoid(), typeListOffset = 0),
            ProtoId(stringIndex(stringSignature), typeMap, typeListOffset = 0)
        ))

        // Method-IDs
        // Method: <init> in HashMap
        methodIds.add(MethodId(typeHashMap, protoInit, stringIndex(stringInit)))
        // Method: put in HashMap
        methodIds.add(MethodId(typeHashMap, protoExecute, stringIndex(stringPut)))
        // Method: executeAction in our class
        methodIds.add(MethodId(typeClass, protoExecute, stringIndex(stringMethodName)))

        // Class-Defs
        classDefs.add(
            ClassDef(
                classIdx = typeClass,
                accessFlags = 0x0001, // PUBLIC
                superclassIdx = typeSuperclass,
                interfacesOffset = 0, // will be calculated
                sourceFileIdx = -1, // no source file
                annotationsOffset = 0,
                classDataOffset = 0, // will be calculated
                staticValuesOffset = 0
            )
        )

        // Type-List für Interfaces (DynamicPluginModule)
        val interfaceTypeList = byteArrayOf(
            0x00, 0x01, // size = 1
            0x00, 0x02  // type_idx = DynamicPluginModule
        )

        // Class-Data encoded fields + methods
        // Fields: 0 static, 0 instance
        // Methods: 1 direct (<init>), 1 virtual (executeAction)
        val classData = encodeClassData(
            staticFieldsSize = 0,
            instanceFieldsSize = 0,
            directMethods = listOf(
                EncodedMethod(0x1000, 0, stringIndex(stringInit), protoInit, null) // <init>
            ),
            virtualMethods = listOf(
                EncodedMethod(0x1001, 0, stringIndex(stringMethodName), protoExecute, generateExecuteActionCode())
            )
        )

        // Code für executeAction
        val executeActionCode = generateExecuteActionCode()

        // Jetzt alles zusammenbauen
        return assembleDex(
            strings = strings,
            typeIds = typeIds,
            protoIds = protoIds,
            methodIds = methodIds,
            classDefs = classDefs,
            interfaceTypeList = interfaceTypeList,
            classData = classData,
            codeItems = mapOf(1 to executeActionCode) // method_idx 1 = executeAction
        )
    }

    private fun typeVoid(): Int = 0x10 // TYPE_VOID

    // =========================================================================
    // DALVIK BYTECODE GENERATION
    // =========================================================================

    private fun generateExecuteActionCode(): ByteArray {
        // Generiert Dalvik-Bytecode für:
        // HashMap<String, Object> result = new HashMap<>();
        // result.put("status", "ok");
        // result.put("tool", "generated");
        // return result;

        val code = ByteArray(128)
        var idx = 0

        // new-instance v0, Ljava/util/HashMap;
        code[idx++] = 0x22.toByte() // opcode new-instance
        code[idx++] = 0x00 // v0
        code[idx++] = 0x00.toByte() // type_idx = HashMap (low byte)
        code[idx++] = 0x03.toByte() // type_idx = HashMap (high byte)

        // invoke-direct {v0}, Ljava/util/HashMap;-><init>()V
        code[idx++] = 0x6E.toByte() // opcode invoke-direct
        code[idx++] = 0x00 // {v0}
        code[idx++] = 0x00.toByte() // method_idx = <init> (low)
        code[idx++] = 0x00.toByte() // method_idx = <init> (high)

        // const-string v1, "status"
        code[idx++] = 0x1B.toByte() // opcode const-string
        code[idx++] = 0x01 // v1
        code[idx++] = 0x06.toByte() // string_idx = "status" (low)
        code[idx++] = 0x00.toByte() // string_idx = "status" (high)

        // const-string v2, "ok"
        code[idx++] = 0x1B.toByte() // opcode const-string
        code[idx++] = 0x02 // v2
        code[idx++] = 0x07.toByte() // string_idx = "ok" (low)
        code[idx++] = 0x00.toByte() // string_idx = "ok" (high)

        // invoke-virtual {v0, v1, v2}, Ljava/util/HashMap;->put(...)
        code[idx++] = 0x6F.toByte() // opcode invoke-virtual
        code[idx++] = 0x07 // {v0, v1, v2}
        code[idx++] = 0x02.toByte() // method_idx = put (low)
        code[idx++] = 0x00.toByte() // method_idx = put (high)

        // move-result-object v0
        code[idx++] = 0x0D.toByte() // opcode move-result-object
        code[idx++] = 0x00 // v0

        // const-string v1, "tool"
        code[idx++] = 0x1B.toByte()
        code[idx++] = 0x01
        code[idx++] = 0x08.toByte()
        code[idx++] = 0x00.toByte()

        // const-string v2, "generated"
        code[idx++] = 0x1B.toByte()
        code[idx++] = 0x02
        code[idx++] = 0x09.toByte()
        code[idx++] = 0x00.toByte()

        // invoke-virtual {v0, v1, v2}, Ljava/util/HashMap;->put(...)
        code[idx++] = 0x6F.toByte()
        code[idx++] = 0x07
        code[idx++] = 0x02.toByte()
        code[idx++] = 0x00.toByte()

        // move-result-object v0
        code[idx++] = 0x0D.toByte()
        code[idx++] = 0x00

        // return-object v0
        code[idx++] = 0x11.toByte() // opcode return-object
        code[idx++] = 0x00 // v0

        // Padding
        while (idx < code.size) {
            code[idx++] = 0x00 // nop
        }

        return encodeCodeItem(code.copyOfRange(0, idx))
    }

    private fun encodeCodeItem(instructions: ByteArray): ByteArray {
        // Code item header: 40 bytes + instructions (aligned to 4 bytes)
        val alignedSize = ((instructions.size + 3) / 4) * 4
        val codeItem = ByteArray(40 + alignedSize)

        var idx = 0
        // registers_size = 3 (v0, v1, v2)
        codeItem[idx++] = 0x03 // registers_size (low)
        codeItem[idx++] = 0x00 // registers_size (high)
        // ins_size = 1 (1 input parameter: Map)
        codeItem[idx++] = 0x01 // ins_size (low)
        codeItem[idx++] = 0x00 // ins_size (high)
        // outs_size = 0
        codeItem[idx++] = 0x00 // outs_size (low)
        codeItem[idx++] = 0x00 // outs_size (high)
        // tries_size = 0
        codeItem[idx++] = 0x00 // tries_size (low)
        codeItem[idx++] = 0x00 // tries_size (high)
        // debug_info_off = 0
        for (i in 0 until 4) codeItem[idx++] = 0x00
        // insns_size
        val insnsSize = alignedSize / 2
        codeItem[idx++] = (insnsSize and 0xFF).toByte()
        codeItem[idx++] = ((insnsSize shr 8) and 0xFF).toByte()
        codeItem[idx++] = ((insnsSize shr 16) and 0xFF).toByte()
        codeItem[idx++] = ((insnsSize shr 24) and 0xFF).toByte()
        // instructions
        System.arraycopy(instructions, 0, codeItem, idx, instructions.size)
        // rest is zero-padded

        return codeItem
    }

    // =========================================================================
    // ENCODING HELPERS
    // =========================================================================

    private fun encodeClassData(
        staticFieldsSize: Int,
        instanceFieldsSize: Int,
        directMethods: List<EncodedMethod>,
        virtualMethods: List<EncodedMethod>
    ): ByteArray {
        val buffer = mutableListOf<Byte>()

        // static_fields_size
        encodeULEB128(buffer, staticFieldsSize)
        // instance_fields_size
        encodeULEB128(buffer, instanceFieldsSize)
        // direct_methods_size
        encodeULEB128(buffer, directMethods.size)
        directMethods.forEach { encodeEncodedMethod(buffer, it) }
        // virtual_methods_size
        encodeULEB128(buffer, virtualMethods.size)
        virtualMethods.forEach { encodeEncodedMethod(buffer, it) }

        return buffer.toByteArray()
    }

    private fun encodeEncodedMethod(buffer: MutableList<Byte>, method: EncodedMethod) {
        // method_index_diff (ULEB128)
        encodeULEB128(buffer, method.methodIndexDiff)
        // access_flags (ULEB128)
        encodeULEB128(buffer, method.accessFlags)
        // code_off (ULEB128) - 0 if no code, otherwise offset
        if (method.codeItem != null) {
            encodeULEB128(buffer, method.codeOff)
        } else {
            encodeULEB128(buffer, 0)
        }
    }

    private fun encodeULEB128(buffer: MutableList<Byte>, value: Int) {
        var v = value
        while (v >= 0x80) {
            buffer.add((v or 0x80).toByte())
            v = v shr 7
        }
        buffer.add(v.toByte())
    }

    // =========================================================================
    // DEX ASSEMBLY
    // =========================================================================

    private data class ProtoId(
        val shortyIdx: Int,
        val returnTypeIdx: Int,
        val typeListOffset: Int
    )

    private data class MethodId(
        val classIdx: Int,
        val protoIdx: Int,
        val nameIdx: Int
    )

    private data class ClassDef(
        val classIdx: Int,
        val accessFlags: Int,
        val superclassIdx: Int,
        val interfacesOffset: Int,
        val sourceFileIdx: Int,
        val annotationsOffset: Int,
        val classDataOffset: Int,
        val staticValuesOffset: Int
    )

    private data class EncodedMethod(
        val methodIndexDiff: Int,
        val accessFlags: Int,
        val methodNameIdx: Int,
        val protoIdx: Int,
        val codeItem: ByteArray?
    ) {
        val codeOff: Int get() = if (codeItem != null) 0x1000 else 0 // placeholder offset
    }

    private fun assembleDex(
        strings: List<String>,
        typeIds: List<Int>,
        protoIds: List<ProtoId>,
        methodIds: List<MethodId>,
        classDefs: List<ClassDef>,
        interfaceTypeList: ByteArray,
        classData: ByteArray,
        codeItems: Map<Int, ByteArray>
    ): ByteArray {
        // Berechne Offsets für alle Sektionen
        val headerSize = 112
        val stringIdsSize = 4 + strings.size * 4
        val typeIdsSize = 4 + typeIds.size * 4
        val protoIdsSize = 4 + protoIds.size * 12
        val methodIdsSize = 4 + methodIds.size * 8
        val classDefsSize = 4 + classDefs.size * 32
        val mapListSize = 4 + 7 * 20 // 7 items

        // Data-Sektion: Strings + Type-List + Class-Data + Code-Items
        val stringData = encodeStringData(strings)
        val dataSize = stringData.size + interfaceTypeList.size + classData.size + codeItems.values.sumOf { it.size }

        // Alignment
        val align = { offset: Int -> ((offset + 3) / 4) * 4 }

        var offset = headerSize
        val stringIdsOff = offset
        offset = align(offset + stringIdsSize)
        val typeIdsOff = offset
        offset = align(offset + typeIdsSize)
        val protoIdsOff = offset
        offset = align(offset + protoIdsSize)
        val methodIdsOff = offset
        offset = align(offset + methodIdsSize)
        val classDefsOff = offset
        offset = align(offset + classDefsSize)
        val mapListOff = offset
        offset = align(offset + mapListSize)
        val dataOff = offset
        offset = align(offset + dataSize)

        val fileSize = offset

        // Erstelle ByteBuffer
        val dex = ByteArray(fileSize)
        var pos = 0

        // ===== HEADER =====
        // Magic
        dex[pos++] = 0x64 // 'd'
        dex[pos++] = 0x65 // 'e'
        dex[pos++] = 0x78 // 'x'
        dex[pos++] = 0x0A // '\n'
        dex[pos++] = 0x30 // '0'
        dex[pos++] = 0x35 // '5'
        dex[pos++] = 0x00 // '\0'
        dex[pos++] = 0x00 // '\0'

        // Checksum (Adler32) - placeholder, wird später berechnet
        for (i in 0 until 4) dex[pos++] = 0x00

        // Signature (SHA-1) - placeholder
        for (i in 0 until 20) dex[pos++] = 0x00

        // file_size
        writeInt(dex, pos, fileSize)
        pos += 4

        // header_size
        writeInt(dex, pos, headerSize)
        pos += 4

        // endian_tag (little-endian)
        writeInt(dex, pos, 0x12345678)
        pos += 4

        // link_size, link_off
        writeInt(dex, pos, 0)
        pos += 4
        writeInt(dex, pos, 0)
        pos += 4

        // map_off
        writeInt(dex, pos, mapListOff)
        pos += 4

        // string_ids_size, string_ids_off
        writeInt(dex, pos, strings.size)
        pos += 4
        writeInt(dex, pos, stringIdsOff)
        pos += 4

        // type_ids_size, type_ids_off
        writeInt(dex, pos, typeIds.size)
        pos += 4
        writeInt(dex, pos, typeIdsOff)
        pos += 4

        // proto_ids_size, proto_ids_off
        writeInt(dex, pos, protoIds.size)
        pos += 4
        writeInt(dex, pos, protoIdsOff)
        pos += 4

        // field_ids_size, field_ids_off
        writeInt(dex, pos, 0)
        pos += 4
        writeInt(dex, pos, 0)
        pos += 4

        // method_ids_size, method_ids_off
        writeInt(dex, pos, methodIds.size)
        pos += 4
        writeInt(dex, pos, methodIdsOff)
        pos += 4

        // class_defs_size, class_defs_off
        writeInt(dex, pos, classDefs.size)
        pos += 4
        writeInt(dex, pos, classDefsOff)
        pos += 4

        // data_size, data_off
        writeInt(dex, pos, dataSize)
        pos += 4
        writeInt(dex, pos, dataOff)
        pos += 4

        // ===== STRING IDs =====
        pos = stringIdsOff
        pos += 4 // skip size
        var stringDataOffset = dataOff
        strings.forEach { _ ->
            writeInt(dex, pos, stringDataOffset)
            pos += 4
        }

        // ===== TYPE IDs =====
        pos = typeIdsOff
        pos += 4 // skip size
        typeIds.forEach { typeIdx ->
            writeInt(dex, pos, typeIdx)
            pos += 4
        }

        // ===== PROTO IDs =====
        pos = protoIdsOff
        pos += 4 // skip size
        protoIds.forEach { proto ->
            writeInt(dex, pos, proto.shortyIdx)
            pos += 4
            writeInt(dex, pos, proto.returnTypeIdx)
            pos += 4
            writeInt(dex, pos, proto.typeListOffset)
            pos += 4
        }

        // ===== METHOD IDs =====
        pos = methodIdsOff
        pos += 4 // skip size
        methodIds.forEach { method ->
            writeInt(dex, pos, method.classIdx)
            pos += 4
            writeInt(dex, pos, method.protoIdx)
            pos += 4
            writeInt(dex, pos, method.nameIdx)
            pos += 4
        }

        // ===== CLASS DEFS =====
        pos = classDefsOff
        pos += 4 // skip size
        classDefs.forEach { classDef ->
            writeInt(dex, pos, classDef.classIdx)
            pos += 4
            writeInt(dex, pos, classDef.accessFlags)
            pos += 4
            writeInt(dex, pos, classDef.superclassIdx)
            pos += 4
            writeInt(dex, pos, classDef.interfacesOffset)
            pos += 4
            writeInt(dex, pos, classDef.sourceFileIdx)
            pos += 4
            writeInt(dex, pos, classDef.annotationsOffset)
            pos += 4
            writeInt(dex, pos, classDef.classDataOffset)
            pos += 4
            writeInt(dex, pos, classDef.staticValuesOffset)
            pos += 4
        }

        // ===== MAP LIST =====
        pos = mapListOff
        writeInt(dex, pos, 7) // 7 map items
        pos += 4
        // String ID item
        writeMapItem(dex, pos, 0x0001, strings.size, stringIdsOff)
        pos += 20
        // Type ID item
        writeMapItem(dex, pos, 0x0002, typeIds.size, typeIdsOff)
        pos += 20
        // Proto ID item
        writeMapItem(dex, pos, 0x0003, protoIds.size, protoIdsOff)
        pos += 20
        // Method ID item
        writeMapItem(dex, pos, 0x0005, methodIds.size, methodIdsOff)
        pos += 20
        // Class Def item
        writeMapItem(dex, pos, 0x0006, classDefs.size, classDefsOff)
        pos += 20
        // Type List item (interfaces)
        writeMapItem(dex, pos, 0x1001, 1, dataOff)
        pos += 20
        // Class Data item
        writeMapItem(dex, pos, 0x2000, 1, dataOff + stringData.size + interfaceTypeList.size)
        pos += 20

        // ===== DATA SECTION =====
        pos = dataOff
        // String data
        System.arraycopy(stringData, 0, dex, pos, stringData.size)
        pos += stringData.size
        // Interface type list
        System.arraycopy(interfaceTypeList, 0, dex, pos, interfaceTypeList.size)
        pos += interfaceTypeList.size
        // Class data
        System.arraycopy(classData, 0, dex, pos, classData.size)
        pos += classData.size
        // Code items
        codeItems.forEach { (_, codeItem) ->
            System.arraycopy(codeItem, 0, dex, pos, codeItem.size)
            pos += codeItem.size
        }

        // ===== CHECKSUM & SIGNATURE =====
        // Adler32 über Header ab Offset 12
        val adler = Adler32()
        adler.update(dex, 12, dex.size - 12)
        val checksum = adler.value
        writeInt(dex, 8, checksum.toInt())

        // SHA-1 Signatur
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(dex, 12, dex.size - 12)
        val signature = sha1.digest()
        System.arraycopy(signature, 0, dex, 12 + 4, 20)

        return dex
    }

    private fun encodeStringData(strings: List<String>): ByteArray {
        // String-Data-Format: size (ULEB128) + utf16_size (ULEB128) + data (MUTF-8)
        val buffer = mutableListOf<Byte>()
        strings.forEach { str ->
            val bytes = str.toByteArray(Charsets.UTF_8)
            encodeULEB128(buffer, bytes.size + 1) // +1 for null terminator
            encodeULEB128(buffer, bytes.size)
            buffer.addAll(bytes.toList())
            buffer.add(0x00) // null terminator
        }
        return buffer.toByteArray()
    }

    private fun writeInt(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeMapItem(buf: ByteArray, offset: Int, type: Int, size: Int, offsetVal: Int) {
        writeShort(buf, offset, type)
        writeShort(buf, offset + 2, 0) // unused
        writeInt(buf, offset + 4, size)
        writeInt(buf, offset + 8, offsetVal)
        writeInt(buf, offset + 12, 0) // reserved
        writeInt(buf, offset + 16, 0) // reserved
    }

    private fun writeShort(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
