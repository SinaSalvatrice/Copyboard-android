interface SearchBoxProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}

export function SearchBox({ value, onChange, placeholder = 'Search snippets' }: SearchBoxProps) {
  return (
    <label className="search-box">
      <span className="search-box__label">Search</span>
      <input
        className="search-box__input"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
      />
    </label>
  );
}