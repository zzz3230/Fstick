import tkinter as tk
from tkinter import filedialog, messagebox
from minio import Minio
from minio.error import S3Error
import os


class DualPluginUploader:
    def __init__(self, root):
        self.root = root
        self.root.title("Plugin Uploader 🚀")
        self.root.geometry("700x500")

        self.client_files = []
        self.server_files = []

        # --- MinIO config ---
        tk.Label(root, text="MinIO URL").pack()
        self.endpoint = tk.Entry(root, width=60)
        self.endpoint.insert(0, "http://minio:9000")
        self.endpoint.pack()

        tk.Label(root, text="Access Key").pack()
        self.access = tk.Entry(root, width=60)
        self.access.insert(0, "admin")
        self.access.pack()

        tk.Label(root, text="Secret Key").pack()
        self.secret = tk.Entry(root, width=60, show="*")
        self.secret.insert(0, "11111111")
        self.secret.pack()

        tk.Label(root, text="Bucket").pack()
        self.bucket = tk.Entry(root, width=60)
        self.bucket.insert(0, "f-stick-plugins")
        self.bucket.pack()

        # --- Plugin metadata ---
        tk.Label(root, text="Plugin ID").pack()
        self.plugin_id = tk.Entry(root, width=60)
        self.plugin_id.pack()

        tk.Label(root, text="Version").pack()
        self.version = tk.Entry(root, width=60)
        self.version.insert(0, "1.0.0")
        self.version.pack()

        # --- CLIENT ---
        tk.Label(root, text="\n🟦 CLIENT (cl / js)").pack()

        tk.Button(root, text="Выбрать CLIENT файлы", command=self.pick_client).pack()

        self.client_label = tk.Label(root, text="Нет файлов")
        self.client_label.pack()

        # --- SERVER ---
        tk.Label(root, text="\n🟥 SERVER (sv / lua)").pack()

        tk.Button(root, text="Выбрать SERVER файлы", command=self.pick_server).pack()

        self.server_label = tk.Label(root, text="Нет файлов")
        self.server_label.pack()

        # --- upload ---
        tk.Button(root, text="UPLOAD 🚀", bg="green", fg="white", command=self.upload).pack(pady=20)

    def pick_client(self):
        self.client_files = filedialog.askopenfilenames()
        self.client_label.config(text="\n".join(self.client_files))

    def pick_server(self):
        self.server_files = filedialog.askopenfilenames()
        self.server_label.config(text="\n".join(self.server_files))

    def build_path(self, file_path, side, lang):
        plugin_id = self.plugin_id.get()
        version = self.version.get()
        file_name = os.path.basename(file_path)

        return f"plugins/{plugin_id}/versions/{version}/{side}/{lang}/{version}/{file_name}"

    def upload_files(self, client, bucket, files, side, lang):
        for f in files:
            object_name = self.build_path(f, side, lang)
            client.fput_object(bucket, object_name, f)
            print("Uploaded:", object_name)

    def upload(self):
        if not self.client_files and not self.server_files:
            messagebox.showerror("Error", "Нет файлов")
            return

        try:
            endpoint = self.endpoint.get().replace("http://", "").replace("https://", "")

            client = Minio(
                endpoint,
                access_key=self.access.get(),
                secret_key=self.secret.get(),
                secure=False
            )

            bucket = self.bucket.get()

            if not client.bucket_exists(bucket):
                client.make_bucket(bucket)

            self.upload_files(client, bucket, self.client_files, "cl", "js")
            self.upload_files(client, bucket, self.server_files, "sv", "lua")

            messagebox.showinfo("OK", "Заливка завершена 🚀")

        except S3Error as e:
            messagebox.showerror("MinIO error", str(e))
        except Exception as e:
            messagebox.showerror("Error", str(e))


if __name__ == "__main__":
    root = tk.Tk()
    app = DualPluginUploader(root)
    root.mainloop()