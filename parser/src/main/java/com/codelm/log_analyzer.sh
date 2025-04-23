count_frequencies() {
    local log_file=$1
    local output_file=$2
    local label=$3

    # Check if the log file exists
    if [[ -f "$log_file" ]]; then
        echo "Counting all $label..."
        # tr -d '\r' kills Windows \r characters (for displaying counts on new line).
        tr -d '\r' < "$log_file" \
            | sort \
            | uniq -c \
            | sort -rn \
            | while read -r count item; do
                echo "$item: $count"
            done > "$output_file"
        echo "Counts saved to $output_file"
    else
        echo "Log file not found: $log_file"
    fi
}

# Paths to logs and output files
literals_log="logs/literals.log"
identifiers_log="logs/identifiers.log"
literals_output="logs/literals_count.txt"
identifiers_output="logs/identifiers_count.txt"

# Count frequencies of literals and identifiers
count_frequencies "$literals_log" "$literals_output" "literals"
count_frequencies "$identifiers_log" "$identifiers_output" "identifiers"