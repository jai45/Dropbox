import { useState } from "react";
import styles from "./Modal.module.css";

const Modal = ({
  isOpen,
  onClose,
  onSubmit,
  title,
  placeholder,
  submitText = "Create",
}) => {
  const [value, setValue] = useState("");

  const handleClose = () => {
    setValue("");
    onClose();
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (value.trim()) {
      onSubmit(value.trim());
      handleClose();
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === "Escape") {
      handleClose();
    }
  };

  if (!isOpen) return null;

  return (
    <div className={styles.overlay} onClick={handleClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <h3>{title}</h3>
          <button onClick={handleClose} className={styles.closeBtn}>
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <input
            type="text"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={placeholder}
            className={styles.input}
            autoFocus
          />

          <div className={styles.actions}>
            <button
              type="button"
              onClick={handleClose}
              className={styles.cancelBtn}
            >
              Cancel
            </button>
            <button
              type="submit"
              className={styles.submitBtn}
              disabled={!value.trim()}
            >
              {submitText}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default Modal;
