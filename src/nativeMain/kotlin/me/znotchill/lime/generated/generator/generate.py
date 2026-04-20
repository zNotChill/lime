import json
import os
import re

def to_pascal_case(snake_str):
    return "".join(x.capitalize() for x in snake_str.lower().split("_"))

def extract_packet_names(data, state, direction):
    try:
        types = data[state][direction]["types"]
        fields = types["packet"][1]
        name_field = next(f for f in fields if f.get("name") == "name")
        mappings = name_field["type"][1]["mappings"]
        return mappings
    except: return {}

def run_conversion(input_dir, output_dir):
    all_packets = {
        "Serverbound": {"handshake": set(), "status": set(), "login": set(), "config": set(), "play": set()},
        "Clientbound": {"status": set(), "login": set(), "config": set(), "play": set()}
    }

    protocol_blocks = []

    for filename in sorted(os.listdir(input_dir)):
        if not filename.endswith(".json"): continue
        version = re.sub(r"\D", "", filename)
        with open(os.path.join(input_dir, filename), 'r') as f:
            data = json.load(f)

            block = [f"    val protocol{version} = ProtocolPackets(", f"        version = {version},"]

            for direction in ["Serverbound", "Clientbound"]:
                dir_key = "toServer" if direction == "Serverbound" else "toClient"

                fixed_dir = direction.replace("bound", "")
                block.append(f"        {direction.lower()} = {fixed_dir}ProtocolPackets(")

                for state in ["handshake", "status", "login", "configuration", "play"]:
                    if direction == "Clientbound" and state == "handshake": continue

                    mappings = extract_packet_names(data, state, dir_key)

                    if not mappings and state == "handshake" and direction == "Serverbound":
                        sorted_names = ["intention"]
                    else:
                        sorted_names = [mappings[k] for k in sorted(mappings.keys(), key=lambda x: int(x, 16))]

                    state_key = "config" if state == "configuration" else state
                    block.append(f"            {state_key} = listOf({', '.join([f'\"{n}\"' for n in sorted_names])}),")
                    for name in sorted_names:
                        all_packets[direction][state_key].add((name, to_pascal_case(name)))
                block.append("        ),")
            block.append("    )")
            protocol_blocks.append("\n".join(block))

    write_protocol_file(output_dir, protocol_blocks)
    write_packet_file(output_dir, all_packets)

def write_protocol_file(output_dir, blocks):
    content = f"""package me.znotchill.lime.generated
import me.znotchill.lime.registries.*

object Protocol {{
{chr(10).join(blocks)}
}}"""
    with open(os.path.join(output_dir, "Protocol.kt"), "w") as f: f.write(content)

def write_packet_file(output_dir, all_packets):
    lines = [
        "package me.znotchill.lime.generated",
        "",
        "import me.znotchill.lime.client.PipeDirection",
        "",
        "interface Identifiable {",
        "    val value: String",
        "    val direction: PipeDirection",
        "}",
        "",
        "object Packet {"
    ]

    for direction, states in all_packets.items():
        enum_dir = "PipeDirection.SERVER" if direction == "Serverbound" else "PipeDirection.CLIENT"

        lines.append(f"    object {direction} {{")
        for state, names in states.items():
            lines.append(f"        enum class {state.capitalize()}(val internal: String) : Identifiable {{")
            for internal, pascal in sorted(list(names)):
                lines.append(f"            {pascal}(\"{internal}\"),")

            lines.append(f"            ;")
            lines.append(f"            override val value: String get() = internal")
            lines.append(f"            override val direction: PipeDirection get() = {enum_dir}")
            lines.append("        }")
        lines.append("    }")
    lines.append("}")

    with open(os.path.join(output_dir, "Packet.kt"), "w") as f: f.write("\n".join(lines))

if __name__ == "__main__":
    run_conversion("protocols/", "../")